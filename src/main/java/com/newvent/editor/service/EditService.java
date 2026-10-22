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

import com.newvent.asset.service.AssetUrls;
import com.newvent.common.exception.code.ErrorCode;
import com.newvent.editor.exception.EditErrorCode;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.LlmDailyLimitExceededException;
import com.newvent.generation.service.ChatLog;
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
import com.newvent.registry.Asset;
import com.newvent.registry.AssetEdit;
import com.newvent.registry.Block;
import com.newvent.registry.BlockMerge;
import com.newvent.registry.BlockValidator;
import com.newvent.registry.ColorWords;
import com.newvent.registry.Layout;
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
    // 채팅 내역. 남기지 못해도 수정은 계속된다
    private final ChatLog chatLog;

    // ★ 이 이벤트에 올라온 이미지 주소. 모델에게 목록을 주고, 돌아온 html 을 그 목록으로 대조한다.
    //   둘 다 해야 한다 — 안내만 하면 모델이 주소를 지어내고, 대조만 하면 쓸 수 있는 줄 모른다
    private final AssetUrls assets;

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

    public EditService(RouteService router, RetryService retry, VersionStore versions,
                       EventGuard guard, GenerationJobStore jobs, LlmCallRecorder recorder,
                       LlmCallGateway gateway, com.newvent.filtering.FilteringPolicy filtering) {
        this(router, retry, versions, guard, jobs, recorder, gateway, filtering, ChatLog.NOOP,
                AssetUrls.NONE);
    }

    public EditService(RouteService router, RetryService retry, VersionStore versions,
                       EventGuard guard, GenerationJobStore jobs, LlmCallRecorder recorder,
                       LlmCallGateway gateway, com.newvent.filtering.FilteringPolicy filtering,
                       ChatLog chatLog) {
        this(router, retry, versions, guard, jobs, recorder, gateway, filtering, chatLog,
                AssetUrls.NONE);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EditService(RouteService router, RetryService retry, VersionStore versions,
                       EventGuard guard, GenerationJobStore jobs, LlmCallRecorder recorder,
                       LlmCallGateway gateway, com.newvent.filtering.FilteringPolicy filtering,
                       ChatLog chatLog, AssetUrls assets) {
        this.assets = assets;
        this.chatLog = chatLog;
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

        // ⑦ 채팅 내역 - 관리자가 고른 영역이 있으면 같이 남기고, 없으면 라우터가 정한 뒤 채운다.
        // 개인정보 후보가 감지된 요청(확인 전 되묻기 · 확인 후 재전송)은 원문 대신 표시만 남긴다.
        boolean privacy = input.question() != null || cmd.privacyConfirmed();
        job.chatRequestId(chatLog.request(cmd.eventId(), input.text(),
                cmd.hasBlocks() ? String.join(",", cmd.blocks()) : null, privacy));

        if (input.question() != null) {
            job.askBack(input.question());
            chatLog.finish(job, cmd.eventId(), null);
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
            // ★ 채팅 내역을 먼저 끝맺는다. 자리를 비우면 다음 요청이 바로 들어와 메시지 순서가 뒤섞일 수 있다.
            //   남기다 터져도 자리는 반드시 비운다.
            try {
                // ★ 일부만 반영된 경우 그 안내를 채팅 내역에도 남긴다 — 작업 상태에만 담으면
                //   대화를 다시 볼 때 "반영했습니다" 만 남아 무엇이 빠졌는지 알 수 없다
                String doneReply = job.message() == null || job.message().isBlank()
                        ? "수정을 반영했습니다."
                        : "수정을 반영했습니다. " + job.message();
                chatLog.finish(job, cmd.eventId(), doneReply);
            } finally {
                // ★ 반드시 비운다. 안 비우면 그 이벤트는 다음 작업을 영원히 못 한다
                jobs.finish(job);
            }
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

        // ★★ 이미지 요청은 여기서 끝난다 — **모델을 한 번도 안 부른다.**
        //   "1번 이미지를 배경으로" 에는 모델이 더할 판단이 없다(몇 번 · 어떤 역할 · 어느 영역).
        //   그런데 부르면 영역을 통째로 다시 써서 문구가 같이 바뀌고, 먼저 넣어 둔 다른
        //   이미지가 사라진다. 안 부르면 그 일이 생기지 않는다. AssetEdit 주석 참고.
        if (applyAssetEdit(job, cmd, doc, base)) return;

        // ★ 생략된 후속 요청이면 직전 요청 한 줄을 들고 간다 — 없으면 null 이고 어디에도 안 붙는다.
        //   ★★ 라우터보다 **먼저** 읽는다. 라우터가 op 을 고르는 데도 이 줄이 필요하다 —
        //     없으면 "자주 묻는 질문도 그렇게 바꿔줘" 를 STYLE 로 읽어 모양만 바뀌었다(실측).
        String previous = FollowUp.needsPrevious(cmd.requestText())
                ? chatLog.previousRequest(cmd.eventId(), job.chatRequestId())
                : null;

        List<Decision.Run> plan = planAll(job, cmd, doc, previous);
        if (plan == null) return;               // 되묻기 · 거절 — job 에 문구가 담겼다
        if (!cmd.hasBlocks()) {
            // 관리자가 영역을 안 골랐으면 라우터가 정한 영역을 채팅 내역에 남긴다
            chatLog.target(job.chatRequestId(), plan.stream().map(r -> r.block().key()).distinct()
                    .collect(java.util.stream.Collectors.joining(",")));
        }
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

            Applied applied = apply(job, cmd, doc, step, plan.size() > 1, previous);
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

        // ★★ 일부만 안 바뀌었으면 **그 사실을 말한다.** 전에는 noChange 를 모아 놓고
        //   위 "전부 안 바뀜" 검사에만 쓰고 버렸다 — "A 하고 B 해줘" 에서 A 만 되어도
        //   안내는 A 의 요약만 나가서, 관리자는 B 도 된 줄 알았다(실측).
        //   단계는 옮기지 않는다. 저장은 그대로 진행된다
        if (!noChange.isEmpty()) {
            StringJoiner undone = new StringJoiner(", ");
            for (Decision.Run r : noChange) undone.add(r.block().desc().split("[—.]")[0].strip());
            job.notice(undone + " 영역은 바꾸지 못했습니다. 나머지는 반영했습니다.");
        }

        // ★ 연산을 다 통과한 뒤 한 번만 옮긴다. 진행률은 뒤로 가지 않는다
        job.to(GenerationJob.Phase.VALIDATING);

        // ★ 블록 순서를 맞추고, hero 에 고른 팔레트를 페이지 루트로 옮긴다. 모델은 루트를 못 만진다
        doc = PageShell.settle(doc);

        job.to(GenerationJob.Phase.SAVING);

        String changeSummary = String.join("; ", summaries);
        VersionStore.Saved saved =
                versions.save(cmd.eventId(), doc, base.versionId(), changeSummary, job.chatRequestId());
        log.info("수정 저장 (event={}, versionId={}, v{}, source=v{}) — 연산 {}개",
                cmd.eventId(), saved.versionId(), saved.versionNo(),
                base.versionNo(), plan.size());

        // ★★ 순서가 중요하다 — versionId 를 먼저 넣고 DONE 을 나중에 넣는다
        job.versionId(saved.versionId());
        job.to(GenerationJob.Phase.DONE);
    }

    /**
     * 이미지 요청이면 직접 처리하고 true. 아니면 false — 평소 경로로 간다.
     *
     * ★ 모호한 요청("이 사진 어울리게 넣어줘")에는 false 가 나온다. 그건 모델이 맡는 게 맞다.
     * ★ 바뀐 게 없으면 저장하지 않는다. "반영했어요" 가 나가고 화면은 그대로인 게 제일 나쁘다 —
     *   평소 경로의 "조용한 실패" 검사와 같은 자세다.
     */
    private boolean applyAssetEdit(GenerationJob job, EditCommand cmd, String doc,
                                   VersionStore.Snapshot base) {
        List<Asset.Ref> uploaded = assets.assetsOf(cmd.eventId());
        // ★ 페이지를 같이 준다. "배경 없애줘" 를 받을지 비켜설지는 **지금 사진이 있는지**로 갈린다
        AssetEdit.Result read = AssetEdit.read(cmd.requestText(), uploaded, doc);

        // ★★ 막힌 조합은 **모델에게 안 넘긴다.** 또렷하게 말한 요청이라 모델이 받으면
        //   어떻게든 끼워 넣는다 — 혜택 카드 하나에 사진이 통째로 들어가 레이아웃이 깨졌다.
        //   할 수 없으면 할 수 없다고 말하는 쪽이 낫다
        if (read instanceof AssetEdit.Result.CannotDo no) {
            job.fail(no.message());
            return true;
        }
        if (!(read instanceof AssetEdit.Result.Ok ok)) return false;

        AssetEdit.Plan step = ok.plan();
        String after = AssetEdit.apply(doc, step, uploaded);
        if (unchanged(doc, after)) {
            log.info("이미지 수정 — 바뀐 게 없다 (event={}, {})", cmd.eventId(), step);
            job.fail(step.remove()
                    ? "그 자리에는 이미지가 없어요."
                    : "이미지를 넣지 못했어요. 영역을 바꿔서 다시 말씀해 주세요.");
            return true;
        }

        job.to(GenerationJob.Phase.VALIDATING);
        after = PageShell.settle(after);
        job.to(GenerationJob.Phase.SAVING);

        // ★★ 자리를 안 말하고 뺀 경우에는 role · block 이 **둘 다 null** 이다 (AssetEdit.Plan 참고).
        //   그래서 role 을 먼저 보고, null 이 아닐 때만 자리 이름을 만든다 —
        //   여기서 null 검사를 뒤로 미뤘다가 "1번 이미지 빼줘" 에 NPE 가 났다(실측).
        // ★ 사람이 읽는 이름으로 쓴다. key() · cssClass() 를 그대로 쓰면
        //   버전 이력에 "hero ev-asset-bg" 가 찍힌다 — 관리자가 CSS class 를 읽게 된다.
        String summary = AssetEdit.summary(step);
        VersionStore.Saved saved =
                versions.save(cmd.eventId(), after, base.versionId(), summary, job.chatRequestId());
        log.info("이미지 수정 저장 (event={}, versionId={}, v{}, source=v{}) — 모델 호출 0회",
                cmd.eventId(), saved.versionId(), saved.versionNo(), base.versionNo());

        job.versionId(saved.versionId());
        job.to(GenerationJob.Phase.DONE);
        return true;
    }

    /**
     * 전후가 사실상 같은가. <b>공백과 class 순서는 차이로 보지 않는다.</b>
     *
     * ★ Jsoup 을 한 번 통과시키면 모델이 속성 순서나 따옴표를 바꿔도 같은 글자가 된다.
     *   이걸 안 하면 "줄바꿈 하나 달라졌다" 를 변경으로 세어서 검사가 무력해진다.
     */
    private static final String LAYOUT_SUMMARY = "페이지 배치 변경";

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

    private static final String ASK_UNCLEAR = "요청을 이해하지 못했습니다. 어느 부분을 어떻게 바꿀지 알려 주세요. "
            + "(예: 제목을 '가을 대축제' 로 바꿔줘)";

    /**
     * 무엇을 어떻게 바꾸라는 말인지 알 수 없다고 모델이 판단했는가 (#237).
     *
     * ★ 알 수 없다고 **확실히 답했을 때만** true 다. 호출 실패 · 잘림 · 규격 밖 출력은 모두 false —
     *   판정기 장애가 수정을 막으면 안 된다(생성 · 동작 배치기와 같은 선택). 재시도는 없다.
     * ★ 새 묶음이다 — 라우터는 jobId 묶음을 쓴다. 같이 쓰면 (request_id, attempt_no) 유니크에 걸린다.
     */
    private boolean isUnknownRequest(EditCommand cmd) {
        try {
            LlmCallContext ctx = LlmCallContext.newGroup(cmd.eventId());
            com.newvent.infra.llm.LlmClient.Response res = gateway.call(ctx,
                    com.newvent.infra.llm.LlmClient.Request.router(PromptBuilder.editJudge(),
                            PromptBuilder.editJudgeUser(RequestFilter.clean(cmd.requestText()))));
            Boolean known = res.truncated() ? null
                    : com.newvent.generation.service.RequestVagueness.parseKnown(res.content());
            recorder.recordSingle(ctx, res, known != null ? List.of() : List.of(
                    com.newvent.registry.BlockValidator.Failure.of(
                            res.truncated() ? com.newvent.registry.FailureCode.TRUNCATED
                                    : com.newvent.registry.FailureCode.ROUTER_PARSE,
                            "수정 요청 판정 출력이 규격에 맞지 않습니다.")));
            if (known == null) {
                log.info("수정 요청 판정 출력을 읽지 못해 수정을 진행 (event={})", cmd.eventId());
                return false;
            }
            if (!known) log.info("무엇을 바꿀지 알 수 없다고 판단해 되묻는다 (event={})", cmd.eventId());
            return !known;
        } catch (RuntimeException e) {
            log.warn("수정 요청 판정 실패 — 수정을 진행 (event={})", cmd.eventId(), e);
            return false;
        }
    }

    /**
     * 요청문을 실행 계획으로 바꾼다. <b>모델을 부르는 건 라우터 1회뿐이다.</b>
     *
     */
    private List<Decision.Run> planAll(GenerationJob job, EditCommand cmd, String doc, String previousRequest) {
        job.to(GenerationJob.Phase.ROUTING);

        // 영역을 고른 채 "페이지를 벤토 배치로"
        if (cmd.hasBlocks() && Layout.saysPage(cmd.requestText())
                && Layout.requestedBy(cmd.requestText()).isPresent()) {
            job.fail("페이지 배치는 페이지 전체에 적용되는 값이라 영역을 고른 채로는 바꿀 수 없습니다. "
                    + "영역 선택을 해제하고 다시 요청해 주세요.");
            return null;
        }

        // ★ 무엇을 바꾸라는 말인지 모델이 먼저 본다 (#237) — 라우터는 뜻을 모르는 말을 STYLE:hero 로 읽어
        //   "시발" 이 버전으로 저장됐다. 영역을 골랐어도 요청문이 말이 안 되면 되묻는다.
        if (com.newvent.generation.service.RequestVagueness.hasNoLetters(cmd.requestText()) || isUnknownRequest(cmd)) {
            job.askBack(ASK_UNCLEAR);
            return null;
        }

        // ★ requestId = jobId 다. 라우터 호출만 이 작업과 로그에서 이어진다.
        LlmCallContext ctx = LlmCallContext.of(cmd.eventId(), job.jobId());

        Optional<List<RawRoute>> routed = router.route(ctx, RequestFilter.clean(cmd.requestText()), previousRequest);
        // ★ 관리자가 영역을 골랐으면 "어디를" 은 정해졌다. 라우터 결과는 "무엇을"(op · content)만 쓴다.
        //   라우터가 아무것도 못 읽었어도 되묻지 않는다 — 고른 영역을 EDIT 으로 고친다
        if (cmd.hasBlocks()) {
            routed = Optional.of(restrict(routed.orElse(List.of()), chosenBlocks(cmd), cmd.eventId()));
        }
        // ★ 페이지 배치를 직접 말한 요청인데 라우터가 hero 를 안 짚었으면 hero STYLE 을 더한다 —
        //   배치를 고르는 자리가 hero 다. 실제 처리는 apply() 가 모델 없이 한다
        Optional<Layout> layout = cmd.hasBlocks() ? Optional.empty() : Layout.requestedBy(cmd.requestText());
        if (layout.isPresent()) {
            List<RawRoute> ops = new ArrayList<>(routed.orElse(List.of()));
            // 라우터가 hero 를 두 번 짚는 일이 있다 - "제목을 짧게 바꾸고 매거진 레이아웃으로" 에
            // EDIT(제목) + STYLE(배치). 그대로 두면 아래 중복 검사가 "같은 부분에 대한 요청이 두 개" 로 거절한다.
            List<RawRoute> heroOps = ops.stream().filter(r -> Block.HERO.key().equals(r.target())).toList();
            if (heroOps.size() > 1) {
                RawRoute keep = heroOps.stream().filter(r -> r.content() != null && !r.content().isBlank()).findFirst()
                        .or(() -> heroOps.stream().filter(r -> !Op.STYLE.name().equalsIgnoreCase(r.op())).findFirst())
                        .orElse(heroOps.get(0));
                ops.removeIf(r -> Block.HERO.key().equals(r.target()) && r != keep);
                routed = Optional.of(ops);
            }
            if (heroOps.isEmpty()) {
                ops.add(new RawRoute(Op.STYLE.name(), Block.HERO.key(), null));
                routed = Optional.of(ops);
            }
        }
        if (routed.isEmpty()) {
            // ★ 모델에게 다시 시키지 않는다. 관리자에게 다시 묻는다
            job.askBack(ASK_UNCLEAR);
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
        // ★ 팔레트로 낼 수 없는 색을 말했으면 모델이 팔레트를 고르기 전에 되묻는다.
        //   "바탕색은 노란색으로" 에 노란 팔레트가 없어서, 봄(분홍) 같은 엉뚱한 걸 골라 놓고 "반영했어요" 가 나갔다.
        //   페이지 전체 색감 경로(hero STYLE)에서만 본다 — 영역 하나의 배경은 색 이름 변형(v-surface-yellow …)이 받는다.
        //   영역을 골라 고치는 요청(hasBlocks)에는 팔레트를 안내하지 않으므로 이 길을 안 탄다.
        // ★ 한 요청에 페이지 전체 색과 영역 배경색이 같이 있을 수 있다("제목은 파랑, 혜택 배경은 노랑").
        //   라우터가 hero 말고 STYLE 로 짚은 영역을 넘겨, 그 영역을 가리키는 말이 있는 절의 색은 세지 않는다(PR #226 리뷰).
        if (!cmd.hasBlocks() && plan.stream().anyMatch(r -> r.block() == Block.HERO && r.op() == Op.STYLE)) {
            List<Block> areaBlocks = plan.stream()
                    .filter(r -> r.op() == Op.STYLE && r.block() != Block.HERO)
                    .map(Decision.Run::block).toList();
            Optional<ColorWords.Unsupported> unsupported =
                    ColorWords.firstUnsupported(RequestFilter.clean(cmd.requestText()), areaBlocks);
            if (unsupported.isPresent()) {
                log.info("수정 — 팔레트에 없는 색이라 되묻는다 (event={}, 색={})",
                        cmd.eventId(), unsupported.get().group().name());
                job.askBack(ColorWords.message(unsupported.get()));
                return null;
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
            // ★★ 전에는 여기서 op 을 EDIT 으로 바꿨다. 그러면 "항목 하나 더" 라는 의도가
            //    사라져서, 아래 검증기가 ITEM_ADDED 로 막을 때 그게 맞는지 틀린지 알 수 없었다.
            //    삽입이냐 병합이냐는 apply() 가 before 로 가르므로 op 은 ADD 로 둬도 된다.
            log.info("수정 — {} 는 이미 있다. 영역이 아니라 **항목**을 더하는 요청으로 본다 (event={})",
                    r.block().key(), job.eventId());
            return r;
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
    private Applied apply(GenerationJob job, EditCommand cmd, String doc, Decision.Run step, boolean multiArea,
                          String previousRequest) {
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

        // 페이지 배치를 직접 말한 요청 - 배치는 서버가 정한다. 목록에서 이름 하나를 고르는 일이고
        //   요청문이 이미 골랐다. 모델에게 시켰더니 배치와 함께 hero 모양까지 바꿨다
        Layout serverLayout = null;
        if (block == Block.HERO && !cmd.hasBlocks() && step.op() != Op.ADD) {
            serverLayout = Layout.requestedBy(cmd.requestText()).orElse(null);
        }
        if (serverLayout != null) {
            // 걸 수 없는 배치면 모델을 부르기 전에 말한다 - 저장 때 조용히 떨어지면 화면이 그대로다
            String why = PageShell.layoutBlocked(doc, serverLayout);
            if (why != null) {
                job.fail(why);
                return null;
            }
            boolean heroHasMore = (step.content() != null && !step.content().isBlank())
                    || Layout.asksMoreOfHero(cmd.requestText());
            if (!heroHasMore) {
                log.info("수정 — 페이지 배치 {} (event={}). 모델을 부르지 않는다", serverLayout.cssClass(), cmd.eventId());
                return new Applied(PageShell.pickLayout(doc, serverLayout), LAYOUT_SUMMARY);
            }
        }

        job.to(GenerationJob.Phase.CALLING);

        // ★ 연산마다 새 묶음이다. 아래 로그가 jobId 와 묶음을 잇는 유일한 끈이다 —
        //   llm_call_logs 에는 상위 요청을 가리킬 컬럼이 없다
        LlmCallContext ctx = LlmCallContext.newGroup(cmd.eventId());
        log.info("수정 — {} {} (event={}, job={}, 호출묶음={})",
                block.key(), step.op(), cmd.eventId(), job.jobId(), ctx.requestId());

        // ★ 항목 하나를 더하는 요청인가. 시스템 프롬프트 · 사용자 프롬프트 · 검증기
        //   **세 곳이 같은 답**을 써야 한다. 한 곳이라도 빠지면 지시가 어긋나고,
        //   어긋난 지시를 받은 모델은 그대로 두거나 지어낸다 — 둘 다 실패다
        // ★★ creating — 페이지에 아직 없는 영역을 새로 만드나. true 면 shape 를 준다 (§3-3).
        //   안 넘기면 "일정 · FAQ 영역 추가해줘" 가 empty_schedule · slot_invented 로 4회 전부 터졌다.
        //   createFormBlock 만 받던 오버로드는 지웠다 — 요청문을 빼먹으면 컴파일이 안 되게 뒀다.
        boolean creating = before.isBlank();
        boolean createFormBlock = step.op() == Op.ADD && creating && block.itemsAreFormValues();
        boolean addItem = step.op() == Op.ADD && (!creating || createFormBlock);

        // ★ 한 번만 읽는다. 프롬프트에 주는 목록과 검증기가 대조하는 목록이
        //   **같아야** 한다 — 두 번 읽으면 그 사이에 삭제된 이미지를 안내하게 된다
        List<Asset.Ref> uploaded = assets.assetsOf(cmd.eventId());

        RetryService.Result res;
        try {
            res = retry.runEdit(ctx,
                    // ★ 영역을 골랐으면 hero 에도 페이지 전체 색감(팔레트)을 안내하지 않는다 — 선택 밖이 바뀐다
                    // ★★ step.op() 을 넘긴다. 전에는 안 넘겨서 라우터가 STYLE 로 분류해도
                    //   EDIT 과 **똑같은 프롬프트**가 나갔다 — op 은 위 로그 한 줄에만 쓰였다.
                    //   그래서 "더 화려하게" 에 모델이 이모지만 붙이고 모양은 안 바꿨다.
                    // ★★ addItem 을 넘긴다. 안 넘기면 "항목을 새로 만들거나 지우지 마라" 가
                    //   그대로 나가서, 사용자 프롬프트의 "하나만 더한다" 와 정면으로 어긋난다
                    // ★★ 올라온 이미지 목록을 같이 넘긴다. 비면 안내가 한 줄도 안 나간다
                    // ★★ creating 을 넘긴다. 없는 영역에 shape 를 안 주면 일정·FAQ 추가가 검증에서 전부 실패한다
                    // ★★★ 요청문을 넘긴다. 전에는 createFormBlock 을 받는 오버로드를 부르고 있었는데
                    //   그 안에서 requestText 가 **null 로 박혀** 있었다.
                    //   javadoc 그대로 "null 이면 기본만" 이라, 요청문이 무엇이든 팔레트는 core 19개,
                    //   변형 · 테마도 기본 표본만 실렸다. 확장 144개 팔레트가 모델 앞에 한 번도 안 갔다.
                    //   실측 — "전체색을 노란색으로" 에 노란 팔레트가 0개라, 모델이 매번 다른 색을
                    //   찍었다(제보: "계속 노란색으로 하면 다른 색으로 바뀜"). 붉은색은 축제(빨강·금색)다.
                    //   라우터 · userPrompt 와 같은 정화를 거친 글자를 준다 — 셋이 다른 글자를 보면 안 된다.
                    PromptBuilder.edit(block, isTemplateBlock(before), !cmd.hasBlocks(),
                            step.op() == Op.STYLE, addItem, uploaded,
                            RequestFilter.clean(cmd.requestText()), creating, createFormBlock),
                    userPrompt(cmd, step, before, serverLayout != null, multiArea, previousRequest),
                    // ★ 항목을 하나 더하는 요청이면 검증기에 그렇게 알린다. 안 알리면
                    //   ITEM_ADDED 로 4회가 전부 터지고 관리자는 이유를 못 듣는다
                    // ★★ **같은 목록**을 검증기에도 준다. 여기 없는 src 는 <img> 째로 떨어진다 —
                    //   모델이 지어낸 주소나 바깥 주소가 게시되는 걸 막는 유일한 지점이다
                    HtmlPolicy.edit(block, before, cmd.requestText(), addItem, uploaded));
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

        String summary = res.changeSummary();

        if (summary == null || summary.isBlank()) {
            String blockName = block.desc().split("[—.]")[0].strip();
            summary = blockName + (step.op() == Op.ADD
                ? " 영역 추가"
                : " 영역 수정");
        }

        if (serverLayout != null) {
            boolean heroChanged = !unchanged(PageShell.pickLayout(before, serverLayout),
                    PageShell.pickLayout(html, serverLayout));
            merged = PageShell.pickLayout(merged, serverLayout);
            summary = heroChanged ? summary + "; " + LAYOUT_SUMMARY : LAYOUT_SUMMARY;
        }

        return new Applied(merged, summary);
    }

    /** 섹션들의 palette-* · layout-* class 를 걷어 낸다 — 선택 영역 수정용. 둘 다 페이지 전체 값이다. 다른 class 는 그대로 */
    static String withoutPalette(String html) {
        org.jsoup.nodes.Document d = Jsoup.parseBodyFragment(html);
        d.outputSettings().prettyPrint(false);
        boolean changed = false;
        for (Element el : d.body().select("[class]")) {
            for (String c : List.copyOf(el.classNames())) {
                if (Palette.looksLike(c) || Layout.looksLike(c)) {
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
    /** 사용자에게도 모델에게도 읽히는 영역 이름 — "혜택 — 항목은 폼 값…" 에서 앞 낱말만 */
    private static String areaName(Block block) {
        return block.desc().split("[—.]")[0].strip();
    }

    private static String userPrompt(EditCommand cmd, Decision.Run step, String before, boolean serverSetsLayout,
                                     boolean multiArea, String previousRequest) {
        StringJoiner s = new StringJoiner("\n");
        if (cmd.title() != null && !cmd.title().isBlank()) {
            s.add("이벤트 제목: " + cmd.title().strip());
            s.add("");
        }
        s.add("[관리자 요청]");
        // ★ 배치는 서버가 건다 — 배치를 말한 마디는 모델에게 보여 주지 않는다 (Layout.withoutLayoutTalk).
        //   보여 주면 "좌우로 나눠줘" 를 읽고 hero 자체 모양(v-hero-split)을 바꾼다 — 배치를 서버로 옮긴 이유가 그것이다
        s.add(RequestFilter.clean(serverSetsLayout ? Layout.withoutLayoutTalk(cmd.requestText()) : cmd.requestText()));
        s.add("");

        // ★★ "자주 묻는 질문도 **그렇게** 바꿔줘" 처럼 어떻게가 생략된 요청이면 직전 요청을 붙인다.
        //   붙이지 않으면 '반말로' 가 입력 어디에도 없어 모델이 지어낸다(실측 — 답이 "아니요" 가 됐다).
        //   ★ 대상은 주지 않는다. 이 호출이 맡은 영역은 이미 정해져 있고,
        //     직전 요청의 영역까지 따라가면 요청하지 않은 곳이 바뀐다
        if (previousRequest != null && !previousRequest.isBlank()) {
            s.add("[직전 요청 — 위 요청에서 생략된 '어떻게' 가 여기 있다]");
            s.add(RequestFilter.clean(previousRequest).strip());
            s.add("- 위 요청에 어떻게 바꿀지가 없으면 직전 요청의 **방식만** 그대로 따른다.");
            s.add("- 직전 요청이 말한 **영역은 따라가지 마라.** 이번에 고치는 영역은 아래 [현재 내용] 하나뿐이다.");
            s.add("");
        }

        // ★ 영역마다 모델을 따로 부르는데 요청문은 통째로 간다. 여러 영역이 섞인 요청("전체는 파랑으로 바꾸고 혜택 배경은
        //   노랑으로")에서 모델이 다른 영역의 색을 집었다 — 혜택 호출에서 노랑 대신 파랑을 골랐다(실측, PR #226 리뷰).
        //   이 호출이 맡은 영역을 말해 주고 그 영역 몫만 따르게 한다. 영역이 하나뿐인 요청에는 붙이지 않는다.
        if (multiArea) {
            s.add("[이번 호출은 한 영역만 고친다]");
            s.add("- 위 요청에는 다른 영역 이야기도 섞여 있다. **" + areaName(step.block()) + "** 영역에 해당하는 부분만 따른다.");
            s.add("- 다른 영역에 대한 말(색 · 문구 · 모양)은 이 영역에 적용하지 않는다.");
            s.add("");
        }

        if (step.content() != null && !step.content().isBlank()) {
            s.add("[관리자가 직접 쓴 문구 — 이 문구를 그대로 쓴다]");
            s.add(step.content().strip());
            s.add("");
        }

        // ★ 항목 하나를 더하는 요청. 프롬프트의 기본 자세는 "문구만 고쳐라" 라서,
        //   여기서 말해 주지 않으면 모델이 아무것도 안 하고 원본을 돌려준다
        //   ("참여 방법에 선착순 3,000명 추가" 가 그랬다 — 실측).
        if (step.op() == Op.ADD && !before.isBlank()) {
            s.add("[이번 요청은 **항목 하나를 더하는 것**이다]");
            s.add("- 이 영역의 목록에 항목을 **하나만** 더한다. 기존 항목은 그대로 둔다.");
            s.add("- 더하는 내용은 위 관리자 문구를 쓴다. 없는 수치를 지어내지 마라.");
            if (step.block() == Block.BENEFITS) {
                s.add("- 요청한 혜택만 하나 더한다. 요청에 없는 다른 혜택은 만들지 마라.");
            }
            s.add("");
        }

        if (before.isBlank()) {
            s.add("[현재 상태]");
            s.add("이 영역이 아직 없습니다. 새로 만드세요.");
            if (step.op() == Op.ADD && step.block() == Block.BENEFITS) {
                s.add("혜택 블록을 처음부터 복원합니다. 아래 관리자가 적은 내용으로 혜택 항목을 정확히 1개만 만드세요.");
                s.add("<section data-block=\"benefits\" class=\"v-benefits-grid v-surface-tint\"> 안에 소제목과 <ul><li> 하나를 둡니다. <li> 안에 .benefit-icon 이모지와 .benefit-name 혜택명을 둡니다.");
                s.add("요청에 없는 설명, 금액, 수량, 기간은 만들지 마세요. 근거가 없는 .benefit-desc 또는 .benefit-value 는 비워 둔 요소를 만들지 말고 생략하세요.");
            } else if (step.op() == Op.ADD && step.block() == Block.PRIZE) {
                s.add("경품 블록을 표준 서식으로 처음부터 만듭니다. <section data-block=\"prize\" class=\"v-prize-list\"> 안에 <div class=\"block-header\"><div class=\"sub-label\">Prize</div><h2 class=\"title\">경품 안내</h2></div> 와 <ul><li> 경품 항목 하나를 둡니다.");
                s.add("관리자가 적은 경품 내용만 사용하세요. 등수, 당첨 인원, 추첨 조건 등 요청에 없는 내용을 추가하지 마세요.");
            }
        } else {
            s.add("[현재 내용 — 이 영역을 수정한다]");
            s.add(before);
        }

        s.add("");
        s.add("수정한 영역의 HTML을 그대로 반환하고, 마지막에 "
            + "<!-- changeSummary: 실제 변경 내용의 한 줄 요약 --> "
            + "형식의 주석을 붙이세요.");

        return s.toString();
    }
}
