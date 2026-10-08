package com.newvent.editor.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.newvent.common.exception.code.ErrorCode;
import com.newvent.editor.exception.EditErrorCode;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.LlmDailyLimitExceededException;
import com.newvent.generation.service.EventGuard;
import com.newvent.generation.service.GenerationJob;
import com.newvent.generation.service.GenerationJobStore;
import com.newvent.generation.service.HtmlPolicy;
import com.newvent.generation.service.LlmCallRecorder;
import com.newvent.generation.service.RequestFilter;
import com.newvent.generation.service.RetryService;
import com.newvent.generation.service.VersionStore;
import com.newvent.infra.llm.LlmCallContext;
import com.newvent.infra.llm.LlmCallException;
import com.newvent.infra.llm.LlmCallGateway;
import com.newvent.registry.Block;
import com.newvent.registry.BlockMerge;
import com.newvent.registry.BlockValidator;
import com.newvent.registry.PageShell;
import com.newvent.registry.Palette;
import com.newvent.registry.PromptBuilder;
import com.newvent.registry.Variant;

/**
 * 관리자의 채팅 요청 하나를 새 버전까지
 * ★ 모델을 두 종류로 부른다
 *     라우터 1회      "무엇을 어디에" 를 정한다. 재시도 없다
 *     블록 수정 N회    영역마다 재시도 루프 하나. 연산 수만큼 묶음이 생긴다
 *   한 요청의 상한은 {@code 1 + N × maxAttempts()} 다. N 은 최대 3
 *
 * ★ 하나라도 막히면 아무것도 안 한다

 * ★ 저장은 마지막에 한 번뿐이다
 *   연산이 3개여도 버전은 1행이다. 중간에 실패하거나 중단되면 0행이다.

 */
@Service
public class EditService {

    private static final Logger log = LoggerFactory.getLogger(EditService.class);

    private static final Gate GATE = new Gate();

    private final RouteService router;
    private final RetryService retry;
    private final VersionStore versions;
    private final EventGuard guard;
    private final GenerationJobStore jobs;
    private final LlmCallRecorder recorder;


    private final LlmCallGateway gateway;
    private final com.newvent.filtering.FilteringPolicy filtering;

    private record Applied(String html, String changeSummary) {}

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edit");
        t.setDaemon(true);
        return t;
    });

    public EditService(RouteService router, RetryService retry, VersionStore versions,
                       EventGuard guard, GenerationJobStore jobs,
                       LlmCallRecorder recorder, LlmCallGateway gateway) {
        this(router, retry, versions, guard, jobs, recorder, gateway, new com.newvent.filtering.FilteringPolicy());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EditService(RouteService router, RetryService retry, VersionStore versions,
                       EventGuard guard, GenerationJobStore jobs, LlmCallRecorder recorder,
                       LlmCallGateway gateway, com.newvent.filtering.FilteringPolicy filtering) {
        this.filtering = filtering;
        this.router = router;
        this.retry = retry;
        this.versions = versions;
        this.guard = guard;
        this.jobs = jobs;
        this.recorder = recorder;
        this.gateway = gateway;
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    // ── 시작 ──────────────────────────────────────────────────────

    /** 시작 결과. 거부도 정상 흐름이라 예외로 던지지 않는다 */
    public sealed interface StartResult {
        record Started(GenerationJob job) implements StartResult {}

        /** 요청 자체가 잘못됐다. 컨트롤러가 EditException 으로 바꿔 던진다 */
        record Rejected(ErrorCode errorCode) implements StartResult {}

        /** 그 이벤트에 생성이나 수정이 이미 돌고 있다 — 409 */
        record AlreadyRunning(GenerationJob job) implements StartResult {}
    }


    public StartResult start(EditCommand cmd) {
        // ① 이벤트를 건드려도 되는가
        Optional<ErrorCode> blocked = guard.rejectReason(cmd.eventId());
        if (blocked.isPresent()) {
            return new StartResult.Rejected(blocked.get());
        }

        // ② 입력
        Optional<GenerationErrorCode> bad = RequestFilter.reject(cmd.requestText());
        if (bad.isPresent()) {
            return new StartResult.Rejected(bad.get());
        }

        // ②-b 고른 영역 — 레지스트리에 있고 채팅으로 고칠 수 있는 것만. 모델을 부르기 전에 거른다
        if (chosenBlocks(cmd) == null) {
            log.info("수정 거절 — 고를 수 없는 영역 (event={}, blocks={})", cmd.eventId(), cmd.blocks());
            return new StartResult.Rejected(EditErrorCode.INVALID_BLOCK);
        }

        // ③ 고칠 게 있는가 — 여기서 봐야 404 가 관리자에게 도달한다
        VersionStore.Snapshot snapshot = versions.latest(cmd.eventId()).orElse(null);
        if (snapshot == null) {
            return new StartResult.Rejected(EditErrorCode.PAGE_NOT_FOUND);
        }

        var input = filtering.prepare(cmd.requestText(), cmd.privacyConfirmationJobId(), cmd.eventId(),
                "edit", snapshot.versionId(), jobs, cmd.privacyConfirmed(), cmd.title());
        if (input.error() != null) return new StartResult.Rejected(input.error());
        EditCommand safeCommand = new EditCommand(cmd.eventId(),
                cmd.title(), input.text(), cmd.blocks(), cmd.privacyConfirmationJobId(), cmd.privacyConfirmed());

        // ④ 이미 돌고 있나 — **자리를 잡지 않고** 먼저 본다
        Optional<GenerationJob> already = jobs.ofEvent(cmd.eventId());
        if (already.isPresent()) {
            return new StartResult.AlreadyRunning(already.get());
        }

        // ⑤ 하루 상한 — 라우터 1회 + 연산 하나의 재시도 묶음
        if (input.question() == null) gateway.reserve(1 + retry.maxAttempts());

        // ⑥ 자리 잡기
        Optional<GenerationJob> slot = jobs.start(cmd.eventId());
        if (slot.isEmpty()) {
            // ★ 그 사이에 **끝났을** 수도 있다. orElseThrow 면 500 이다
            return jobs.ofEvent(cmd.eventId())
                    .<StartResult>map(StartResult.AlreadyRunning::new)
                    .orElseGet(() -> new StartResult.Rejected(EditErrorCode.ALREADY_RUNNING));
        }

        GenerationJob job = slot.get();
        job.privacyRequest("edit", snapshot.versionId());
        job.privacyConfirmation(input.fingerprint(), input.privacyTypes());
        if (input.question() != null) {
            job.askBack(input.question());
            jobs.finish(job);
        } else {
            worker.submit(() -> runSafely(job, safeCommand));
        }
        return new StartResult.Started(job);
    }

    /** 중단 요청 — 즉시 멈추지 않는다. 연산 사이에서 멈춘다 */
    public boolean cancel(GenerationJob job) {
        if (job.done()) return false;
        job.requestCancel();
        return true;
    }

    // ── 실행 ──────────────────────────────────────────────────────

    /** ★ 여기서 모든 예외를 잡는다. 워커 스레드 밖으로 나가면 아무도 못 본다 */
    private void runSafely(GenerationJob job, EditCommand cmd) {
        try {
            run(job, cmd);
        } catch (RetryService.Aborted e) {
            // ★ 시도 기록은 apply() 가 이미 남겼다 — ctx 가 거기 있다.
            log.warn("수정 실패 — {}차 시도에서 모델 호출 (event={})", e.attempt(), cmd.eventId(), e);
            job.fail("수정 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (LlmDailyLimitExceededException e) {
            // ★ 429 를 HTTP 로 못 보낸다. 이미 202 가 나갔다.
            //   문구를 따로 두는 이유 — 이건 고장이 아니라 내일 되는 일이다
            log.warn("수정 중단 — 하루 호출 상한 (event={}, 상한={}, 사용={})",
                    cmd.eventId(), e.getDailyLimit(), e.getUsed());
            job.fail("오늘 사용할 수 있는 횟수를 모두 썼습니다. 내일 다시 시도해 주세요.");
        } catch (LlmCallException e) {
            log.warn("수정 실패 — 모델 호출 (event={})", cmd.eventId(), e);
            job.fail("수정 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (IllegalArgumentException e) {
            // ★ BlockMerge 다. 검증은 통과했는데 병합 자리를 못 찾았다 —
            log.error("수정 실패 — 블록 병합 (event={}): {}", cmd.eventId(), e.getMessage(), e);
            job.fail("수정 결과를 페이지에 반영하지 못했습니다. 다시 시도해 주세요.");
        } catch (Exception e) {
            log.error("수정 실패 — 예상치 못한 오류 (event={})", cmd.eventId(), e);
            job.fail("페이지를 고치는 중 문제가 생겼습니다. 잠시 후 다시 시도해 주세요.");
        } finally {
            // ★ 반드시 비운다. 안 비우면 그 이벤트는 다음 작업을 영원히 못 한다
            jobs.finish(job);
        }
    }

    private void run(GenerationJob job, EditCommand cmd) {
        job.to(GenerationJob.Phase.PREPARING);
        if (job.checkCancelled()) return;

        // ★ 기준 버전을 여기서 한 번 읽어 끝까지 들고 간다.
        //   저장할 때 다시 읽으면 그 사이에 생긴 버전을 기준으로 삼게 된다
        VersionStore.Snapshot base = versions.latest(cmd.eventId()).orElse(null);
        if (base == null) {
            // start() 에서 이미 봤다. 그 사이에 없어질 경로는 없지만 NPE 로 죽지는 않게 둔다
            job.fail("아직 만들어진 페이지가 없습니다. 먼저 페이지를 만들어 주세요.");
            return;
        }

        String doc = base.html();
        if (cmd.privacyConfirmationJobId() != null
                && !java.util.Objects.equals(base.versionId(), job.privacyRequest().baseVersionId())) {
            job.fail("확인 요청 이후 작업 버전이 변경되었습니다. 다시 요청해 주세요.");
            return;
        }

        job.privacyRequest("edit", base.versionId());
        List<Decision.Run> plan = planAll(job, cmd, doc);
        if (plan == null) return;               // 되묻기 · 거절 — job 에 문구가 담겼다
        if (job.checkCancelled()) return;

        // ★ 모델을 부르는 연산만 센다. DELETE 는 모델을 안 부른다 —
        int modelOps = (int) plan.stream().filter(r -> r.op() != Op.DELETE).count();
        if (modelOps > 0) {
            // checkDailyLimit 은 상태가 없어서 다시 보는 게 맞다.
            gateway.reserve(modelOps * retry.maxAttempts());
        }
        // ★ 아무것도 안 바뀐 연산을 모은다 — 아래 "조용한 실패" 검사에 쓴다
        List<Decision.Run> noChange = new ArrayList<>();
        List<String> summaries = new ArrayList<>();
        for (Decision.Run step : plan) {
            if (job.checkCancelled()) return;
            String beforeStep = BlockValidator.blockOf(doc, step.block());

            Applied applied = apply(job, cmd, doc, step);
            if (applied == null) return;

            doc = applied.html();
            if (unchanged(beforeStep, BlockValidator.blockOf(doc, step.block()))) {
                noChange.add(step);
            } else {
                summaries.add(applied.changeSummary());
            }
        }
        if (job.checkCancelled()) return;

        // ★★ 조용한 실패를 막는다.
        //
        //   실측(여름 수영장): "참여 대상 — 회원을 가운데로", "참여 버튼 — 파란색으로 물고기 느낌나게"
        //   둘 다 화면이 **한 글자도 안 바뀌었는데** "v2 로 반영했어요" 가 나갔다.
        //   관리자는 자기가 잘못 말한 줄 알고 같은 요청을 계속 다시 쓴다.
        //   할 수 없는 일이면 못 한다고 말하는 쪽이 낫다 — 틀린 성공보다 정직한 실패다.
        //
        //   ★ 왜 모델에게 "못 하겠으면 말해라" 라고 안 시키나
        //     모델은 자기가 못 한 걸 모른다. 늘 뭔가를 출력하고 "했다" 고 한다.
        //     서버가 전후를 비교하는 것만이 확실하다. isTemplateBlock 주석이 이미
        //     "모델은 바꿨다고 하는데 화면은 그대로인 게 제일 나쁘다" 고 적어 뒀는데,
        //     그 검사는 템플릿 블록에만 있었고 일반 경로에는 없었다.
        //
        //   ★ 일부만 안 바뀐 건 통과시킨다. 두 가지를 시켰는데 하나만 됐으면
        //     된 쪽은 저장하는 게 맞다. 전부 안 바뀐 경우만 막는다.
        if (noChange.size() == plan.size()) {
            log.info("수정 — 바뀐 게 없다 (event={}) — {}", cmd.eventId(),
                    plan.stream().map(r -> r.op() + ":" + r.block().key()).toList());
            job.fail(cannotDo(plan, cmd.hasBlocks()));
            return;
        }

        // ★ 연산을 다 통과한 뒤 한 번만 옮긴다. 진행률은 뒤로 가지 않는다
        job.to(GenerationJob.Phase.VALIDATING);

        // ★ 블록 순서를 맞추고, hero 에 고른 팔레트를 페이지 루트로 옮긴다. 모델은 루트를 못 만진다
        doc = PageShell.settle(doc);

        job.to(GenerationJob.Phase.SAVING);

        String changeSummary = String.join("; ", summaries);
        VersionStore.Saved saved = versions.save(cmd.eventId(), doc, base.versionId(), changeSummary);
        log.info("수정 저장 (event={}, versionId={}, v{}, source=v{}) — 연산 {}개",
                cmd.eventId(), saved.versionId(), saved.versionNo(),
                base.versionNo(), plan.size());

        // ★★ 순서가 중요하다 — versionId 를 먼저 넣고 DONE 을 나중에 넣는다
        job.versionId(saved.versionId());
        job.to(GenerationJob.Phase.DONE);
    }

    /**
     * 전후가 사실상 같은가. <b>공백과 class 순서는 차이로 보지 않는다.</b>
     *
     * ★ Jsoup 을 한 번 통과시키면 모델이 속성 순서나 따옴표를 바꿔도 같은 글자가 된다.
     *   이걸 안 하면 "줄바꿈 하나 달라졌다" 를 변경으로 세어서 검사가 무력해진다.
     */
    private static boolean unchanged(String before, String after) {
        return normalize(before).equals(normalize(after));
    }

    private static String normalize(String html) {
        if (html == null) return "";
        Document d = Jsoup.parseBodyFragment(html);
        d.outputSettings().prettyPrint(false);
        // ★ 들여쓰기만 다른 것을 "바뀌었다" 로 세면 검사가 통째로 무력해진다.
        //   태그 사이의 공백뿐인 텍스트 노드를 지운다 — 글자 사이 공백은 건드리지 않는다.
        for (org.jsoup.nodes.TextNode t : d.body().select("*").textNodes()) {
            if (t.isBlank()) t.remove();
        }
        // class 는 순서에 의미가 없다 — 정렬해서 비교한다
        for (Element el : d.body().select("[class]")) {
            el.attr("class", new java.util.TreeSet<>(el.classNames()).stream()
                    .collect(java.util.stream.Collectors.joining(" ")));
        }
        return d.body().html().replaceAll("\\s+", " ").strip();
    }

    /**
     * 할 수 없는 요청에 <b>무엇은 할 수 있는지</b>를 붙여서 돌려준다.
     *
     * ★ "못 했습니다" 만 말하면 관리자는 같은 걸 또 쓴다. 실측에서 그랬다.
     *   그 영역에 실제로 있는 모양 이름을 사람 말로 보여주면 다음 요청이 맞는다.
     *
     * ★ 색은 영역마다 못 바꾼다 — 페이지 전체 색감(팔레트)이 hero 에 걸린다.
     *   "참여 버튼을 파란색으로" 가 안 되는 진짜 이유가 이것이고, 그걸 알려줘야 한다.
     *   영역을 골랐으면 hero 라도 팔레트를 떼어 내므로(withoutPalette) hero 도 안 바뀐다 —
     *   hero 가 아닐 때만 안내하면 "hero 골라놓고 파란색으로" 가 이유를 못 듣는다.
     */
    private static String cannotDo(List<Decision.Run> plan, boolean chosen) {
        StringJoiner s = new StringJoiner(" ");
        StringJoiner names = new StringJoiner(", ");
        // ★ 공백으로 자르면 안 된다 — audience("참여 대상")와 cta("참여 버튼")가 둘 다 "참여" 가 된다.
        //   문장 구분자(— 와 .)에서만 자른다.
        for (Decision.Run r : plan) names.add(r.block().desc().split("[—.]")[0].strip());
        s.add(names + " 영역에서 바꿀 수 있는 게 없어 그대로 두었습니다.");

        boolean styled = plan.stream().anyMatch(r -> r.op() == Op.STYLE);
        if (styled) {
            for (Decision.Run r : plan) {
                List<Variant> vs = Variant.of(r.block()).stream()
                        .filter(v -> v.group() == Variant.Group.LAYOUT).toList();
                if (vs.isEmpty()) continue;
                StringJoiner looks = new StringJoiner(", ");
                for (Variant v : vs) looks.add(v.desc().replaceAll("\\s*\\(.*?\\)", ""));
                s.add("고를 수 있는 모양은 " + looks + " 입니다.");
            }
            if (chosen) {
                s.add("색은 페이지 전체에 적용되는 값이라 영역을 고른 채로는 바꿀 수 없습니다. "
                        + "영역 선택을 해제하고 '전체 색감을 파란색으로' 라고 말씀해 주세요.");
                // ★ 안 되는 것만 말하면 관리자가 막힌다. 이 영역에서 되는 축을 같이 준다
                s.add("이 영역만 바꾸려면 밝기나 분위기로 말씀해 주세요 — "
                        + "예: 더 심플하게, 더 화려하게, 흰 배경으로.");
            } else if (plan.stream().noneMatch(r -> r.block() == Block.HERO)) {
                s.add("색은 영역별로 바꿀 수 없고 '전체 색감을 파란색으로' 처럼 페이지 전체로 말씀해 주세요.");
            }
        }
        return s.toString();
    }

    // ── 라우터 + 관문 ─────────────────────────────────────────────

    /**
     * 고른 영역을 Block 으로 — <b>Block 선언 순서</b>로 세운다. 하나라도 못 고르는 영역이면 null.
     *
     * ★ 선언 순서인 이유 — 연산이 그 순서로 실행되고, 저장 직전 정렬(PageShell.settle)과도 같은 순서다.
     *   고른 순서를 따르면 "cta 를 먼저 고르고 hero 를 고른" 요청이 매번 다른 순서로 돈다.
     * ★ 못 고르는 영역 — 레지스트리에 없는 key, 서버 소유(유의사항)
     */
    static List<Block> chosenBlocks(EditCommand cmd) {
        Set<Block> out = EnumSet.noneOf(Block.class);
        for (String key : cmd.blocks()) {
            Optional<Block> b = Block.find(key);
            if (b.isEmpty() || !b.get().canEdit()) return null;
            out.add(b.get());
        }
        return List.copyOf(out);
    }

    /**
     * 라우터 결과를 고른 영역으로 좁힌다.
     *
     *   고른 영역을 라우터가 짚었으면    그 연산을 쓴다 — 지워줘 · 색 바꿔줘 · 따옴표 문구는 요청문에서 읽어야 한다
     *   고른 영역을 라우터가 못 짚었으면  EDIT 으로 채운다 — "이거 바꿔줘" 처럼 영역을 말하지 않은 요청
     *   고른 영역 밖을 짚었으면           버린다 (로그만)
     *   같은 고른 영역에 연산이 여러 개면  첫 연산만 — 질문 여러 개를 ADD 여러 개로 쪼개도 거절되지 않게
     */
    static List<RawRoute> restrict(List<RawRoute> routed, List<Block> chosen, Long eventId) {
        List<RawRoute> out = new ArrayList<>(chosen.size());
        for (Block b : chosen) {
            RawRoute hit = routed.stream().filter(r -> b.key().equals(r.target())).findFirst().orElse(null);
            out.add(hit != null ? hit : new RawRoute(Op.EDIT.name(), b.key(), null));
        }
        List<String> dropped = routed.stream()
                .map(RawRoute::target)
                .filter(t -> chosen.stream().noneMatch(b -> b.key().equals(t)))
                .toList();
        if (!dropped.isEmpty()) {
            log.info("수정 — 고른 영역 밖이라 버린다 (event={}, 고른={}, 버린={})",
                    eventId, chosen.stream().map(Block::key).toList(), dropped);
        }
        return out;
    }

    /**
     * 요청문을 실행 계획으로 바꾼다. <b>모델을 부르는 건 라우터 1회뿐이다.</b>
     *
     */
    private List<Decision.Run> planAll(GenerationJob job, EditCommand cmd, String doc) {
        job.to(GenerationJob.Phase.ROUTING);

        // ★ requestId = jobId 다. 라우터 호출만 이 작업과 로그에서 이어진다.
        LlmCallContext ctx = LlmCallContext.of(cmd.eventId(), job.jobId());

        Optional<List<RawRoute>> routed = router.route(ctx, RequestFilter.clean(cmd.requestText()));
        // ★ 관리자가 영역을 골랐으면 "어디를" 은 정해졌다. 라우터 결과는 "무엇을"(op · content)만 쓴다.
        //   라우터가 아무것도 못 읽었어도 되묻지 않는다 — 고른 영역을 EDIT 으로 고친다
        if (cmd.hasBlocks()) {
            routed = Optional.of(restrict(routed.orElse(List.of()), chosenBlocks(cmd), cmd.eventId()));
        }
        if (routed.isEmpty()) {
            // ★ 모델에게 다시 시키지 않는다. 관리자에게 다시 묻는다
            job.askBack("요청을 이해하지 못했습니다. 어느 부분을 어떻게 바꿀지 알려 주세요. "
                    + "(예: 제목을 '가을 대축제' 로 바꿔줘)");
            return null;
        }

        List<Decision.Run> plan = new ArrayList<>(routed.get().size());
        Set<Block> targets = EnumSet.noneOf(Block.class);

        for (RawRoute raw : routed.get()) {
            switch (GATE.decide(raw)) {
                case Decision.Reject r -> {
                    log.info("수정 거절 (event={}, code={}) — {}",
                            cmd.eventId(), r.code().getCode(), r.message());
                    job.fail(r.message());
                    return null;
                }
                case Decision.AskBack a -> {
                    job.askBack(a.question());
                    return null;
                }
                case Decision.Run r -> {
                    // ★ 같은 영역이 두 번 오면 거절한다.
                    //   [DELETE steps, EDIT steps] 같은 조합은 두 번째에서 기준이 사라져
                    //   병합이 터진다. 라우터가 낼 일이 드물고, 제대로 다루면 복잡해진다
                    if (!targets.add(r.block())) {
                        job.fail("같은 부분에 대한 요청이 두 개입니다. 하나씩 말씀해 주세요.");
                        return null;
                    }
                    Decision.Run fixed = againstDocument(job, doc, r);
                    if (fixed == null) return null;     // job 에 문구가 담겼다
                    plan.add(fixed);
                }
            }
        }
        log.info("수정 계획 (event={}, job={}) — {}", cmd.eventId(), job.jobId(),
                plan.stream().map(r -> r.op() + ":" + r.block().key()).toList());
        return plan;
    }

    /**
     * 문서와 대조해 연산을 교정하거나 거절한다.
     *
     * ★ 있는데 ADD → EDIT 으로 고친다
     *
     * ★ 없는데 ADD 가 아니면 거절한다
     *
     */
    private Decision.Run againstDocument(GenerationJob job, String doc, Decision.Run r) {
        boolean present = !BlockValidator.blockOf(doc, r.block()).isBlank();

        if (r.op() == Op.ADD && present) {
            log.info("수정 — {} 는 이미 있어 ADD 를 EDIT 으로 본다 (event={})",
                    r.block().key(), job.eventId());
            return new Decision.Run(r.block(), Op.EDIT, r.content());
        }

        if (r.op() != Op.ADD && !present) {
            log.info("수정 거절 — {} 영역이 문서에 없다 (event={}, op={})",
                    r.block().key(), job.eventId(), r.op());
            job.fail(r.op() == Op.DELETE
                    ? "지우려는 부분이 이미 없습니다."
                    : "고치려는 부분이 페이지에 없습니다. 먼저 추가해 달라고 말씀해 주세요.");
            return null;
        }
        return r;
    }

    // ── 연산 하나 ─────────────────────────────────────────────────

    /**
     * 연산 하나를 문서에 반영한다.
     */
    private Applied apply(GenerationJob job, EditCommand cmd, String doc, Decision.Run step) {
        Block block = step.block();
        String before = BlockValidator.blockOf(doc, block);

        // ★ 삭제는 모델을 부르지 않는다. 지우는 데 모델이 필요 없다.
        //   canDelete() 가 참인 블록은 필수가 아닌 것들이다(steps · highlight · intro · audience · faq)
        if (step.op() == Op.DELETE) {
            // 문서에 있는지는 planAll 의 againstDocument 가 이미 봤다
            log.info("수정 — {} 삭제 (event={}). 모델을 부르지 않는다", block.key(), cmd.eventId());
            String blockName = block.desc().split("[—.]")[0].strip();
            return new Applied(removeBlock(doc, block), blockName + " 영역 삭제");
        }

        job.to(GenerationJob.Phase.CALLING);

        // ★ 연산마다 새 묶음이다. 아래 로그가 jobId 와 묶음을 잇는 유일한 끈이다 —
        //   llm_call_logs 에는 상위 요청을 가리킬 컬럼이 없다
        LlmCallContext ctx = LlmCallContext.newGroup(cmd.eventId());
        log.info("수정 — {} {} (event={}, job={}, 호출묶음={})",
                block.key(), step.op(), cmd.eventId(), job.jobId(), ctx.requestId());

        RetryService.Result res;
        try {
            res = retry.runEdit(ctx,
                    // ★ 영역을 골랐으면 hero 에도 페이지 전체 색감(팔레트)을 안내하지 않는다 — 선택 밖이 바뀐다
                    // ★★ step.op() 을 넘긴다. 전에는 안 넘겨서 라우터가 STYLE 로 분류해도
                    //   EDIT 과 **똑같은 프롬프트**가 나갔다 — op 은 위 로그 한 줄에만 쓰였다.
                    //   그래서 "더 화려하게" 에 모델이 이모지만 붙이고 모양은 안 바꿨다.
                    PromptBuilder.edit(block, isTemplateBlock(before), !cmd.hasBlocks(),
                            step.op() == Op.STYLE),
                    userPrompt(cmd, step, before),
                    HtmlPolicy.edit(block, before, cmd.requestText()));
        } catch (RetryService.Aborted e) {
            // ★ 터진 시도의 실패 행은 문이 남겼다. 그 앞의 시도들은 아직 아무도 안 남겼다.
            //   ctx 가 여기 있으므로 여기서 남긴다 — runSafely 로 올라가면 못 남긴다
            recorder.recordAttempts(ctx, e.partial());
            throw e;
        }

        // ★ 성공이든 실패든 시도 전부를 남긴다. 취소돼도 쓴 토큰은 쓴 것이다
        recorder.recordAttempts(ctx, res);
        job.attempt(res.attempts());

        if (!res.ok()) {
            log.info("수정 실패 — {} 영역, {}회 시도, 마지막 사유 {}",
                    block.key(), res.attempts(), res.lastFailures());
            job.fail("요청을 반영하지 못했습니다. "
                    + "요청을 조금 더 구체적으로 적어 다시 시도해 주세요.");
            return null;
        }
        // ★ 영역을 골랐는데 모델이 팔레트를 붙였으면 떼어 낸다 — 프롬프트에서 뺐어도 출력은 보장되지 않는다.
        //   남겨 두면 저장 직전 루트로 옮겨져(PageShell.hoistPalette) 고르지 않은 영역의 색까지 바뀐다
        String html = cmd.hasBlocks() ? withoutPalette(res.html()) : res.html();

        // ★ 없던 영역이면 병합이 아니라 삽입이다.
        //   BlockMerge.merge 는 있는 섹션을 갈아끼울 뿐 새로 만들지 못한다
        String merged = before.isBlank()
            ? insertBlock(doc, block, html.strip())
            : BlockMerge.merge(doc, block, html);

        return new Applied(merged, res.changeSummary());
    }

    /** 섹션들의 palette-* class 를 걷어 낸다 — 선택 영역 수정용. 다른 class 는 그대로 */
    static String withoutPalette(String html) {
        org.jsoup.nodes.Document d = Jsoup.parseBodyFragment(html);
        d.outputSettings().prettyPrint(false);
        boolean changed = false;
        for (Element el : d.body().select("[class]")) {
            for (String c : List.copyOf(el.classNames())) {
                if (Palette.looksLike(c)) {
                    el.removeClass(c);
                    changed = true;
                }
            }
            if (el.classNames().isEmpty()) el.removeAttr("class");
        }
        return changed ? d.body().html() : html;
    }

    /**
     * 템플릿에서 온 블록인가 — 루트 섹션에 ev-block 이 붙어 있다.
     *
     * ★ 템플릿 블록에는 모양 변형(v-*)을 안내하지 않는다.
     *   event.css 의 변형 규칙이 :not(.ev-block) 에만 걸려서 골라도 화면이 안 바뀐다.
     *   모델은 "바꿨다" 고 하는데 화면은 그대로인 게 제일 나쁘다.
     */
    private static boolean isTemplateBlock(String before) {
        if (before == null || before.isBlank()) return false;
        Element root = Jsoup.parseBodyFragment(before).body().selectFirst("section[data-block]");
        return root != null && root.hasClass("ev-block");
    }

    /**
     * 없던 섹션을 문서 순서에 맞는 자리에 끼워 넣는다.
     */
    private static String insertBlock(String doc, Block block, String section) {
        for (Block after : Block.values()) {
            if (after.ordinal() <= block.ordinal()) continue;
            if (BlockValidator.blockOf(doc, after).isBlank()) continue;

            int at = doc.indexOf(BlockMerge.extract(doc, after));
            if (at >= 0) {
                return doc.substring(0, at) + section + "\n" + doc.substring(at);
            }
        }
        return doc + "\n" + section;
    }

    /**
     * 섹션을 문서에서 걷어낸다.
     */
    private static String removeBlock(String doc, Block block) {
        return doc.replace(BlockMerge.extract(doc, block), "");
    }

    // ── 프롬프트 ──────────────────────────────────────────────────

    /**
     * 모델에게 보낼 사용자 메시지.
     *
     * ★ 요청문 <b>전체</b>를 준다. 라우터의 content 만으로는 부족하다
     *
     * ★ 대가 — 다른 영역 얘기가 섞여 들어간다
     *
     * ★ 현재 내용을 준다. 이게 없으면 모델은 무엇을 고칠지 모른다
     */
    private static String userPrompt(EditCommand cmd, Decision.Run step, String before) {
        StringJoiner s = new StringJoiner("\n");
        if (cmd.title() != null && !cmd.title().isBlank()) {
            s.add("이벤트 제목: " + cmd.title().strip());
            s.add("");
        }
        s.add("[관리자 요청]");
        s.add(RequestFilter.clean(cmd.requestText()));
        s.add("");

        if (step.content() != null && !step.content().isBlank()) {
            s.add("[관리자가 직접 쓴 문구 — 이 문구를 그대로 쓴다]");
            s.add(step.content().strip());
            s.add("");
        }

        if (before.isBlank()) {
            s.add("[현재 상태]");
            s.add("이 영역이 아직 없습니다. 새로 만드세요.");
        } else {
            s.add("[현재 내용 — 이 영역을 수정하여 JSON의 html 필드에 넣는다]");
            s.add(before);
        }

        s.add("");
        s.add("수정한 영역의 HTML과 실제 변경 내용을 요약한 changeSummary를 "
            + "JSON 객체 하나로 반환하세요.");

        return s.toString();
    }
}
