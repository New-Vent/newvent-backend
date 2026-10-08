package com.newvent.generation.service;

import java.util.List;
import java.util.Optional;
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
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.infra.llm.LlmCallContext;
import com.newvent.infra.llm.LlmCallException;
import com.newvent.infra.llm.LlmCallGateway;
import com.newvent.infra.llm.LlmClient;
import com.newvent.rag.domain.RagChunk;
import com.newvent.rag.service.RagConstants;
import com.newvent.rag.service.SimilarityService;
import com.newvent.registry.BehaviorPlanter;
import com.newvent.registry.Block;
import com.newvent.registry.BlockValidator.Failure;
import com.newvent.registry.FailureCode;
import com.newvent.registry.PageShell;
import com.newvent.registry.PromptBuilder;
import com.newvent.registry.Slot;
import com.newvent.registry.Slots;


/**
 * 관리자의 생성 요청 하나를 **페이지 첫 버전**까지 끌고 간다.
 *
 * ★ 경로가 둘이고 비용이 완전히 다르다
 *     템플릿 — 파일에서 읽어 슬롯만 비운다. **모델을 아예 안 부른다.** 밀리초
 *     백지   — 프롬프트 → 재시도 루프(최대 4회) → 기간 슬롯 심기. 수 초~수십 초
 *   진행률을 한 벌로 만들면 템플릿 경로가 어색해지므로, 단계만 같이 쓰고 내용은 다르다.
 *
 * ★ 실패는 실패로 끝난다
 *
 * ★ 내부 오류 원문을 관리자에게 보여주지 않는다
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final RetryService retry;
    private final TemplateService templates;
    private final VersionStore versions;
    private final EventGuard guard;
    private final GenerationJobStore jobs;
    private final LlmCallRecorder recorder;
    private final com.newvent.filtering.FilteringPolicy filtering;
    // ★ b3: RAG 검색기. 백지 경로(fromBlank)에서만 쓴다
    private final SimilarityService similarity;

    /** 동작 배치기(LLM) 호출 — null 이면 배치기 없이 기본 배치(BehaviorPlanter.plantGenerated)로 간다 (테스트 · 옛 생성자) */
    private final LlmCallGateway gateway;

    // b3: 현재 생성에서 사용된 RAG 청크 ID들 (Aborted 시 로그 갱신용)
    private String ragChunkIds;


    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "generation");
        t.setDaemon(true);
        return t;
    });

    public GenerationService(RetryService retry, TemplateService templates,
                             VersionStore versions, EventGuard guard, GenerationJobStore jobs,
                             LlmCallRecorder recorder, SimilarityService similarity) {
        this(retry, templates, versions, guard, jobs, recorder,
                new com.newvent.filtering.FilteringPolicy(), similarity);
    }

    public GenerationService(RetryService retry, TemplateService templates,
                             VersionStore versions, EventGuard guard, GenerationJobStore jobs,
                             LlmCallRecorder recorder, com.newvent.filtering.FilteringPolicy filtering,
                             SimilarityService similarity) {
        this(retry, templates, versions, guard, jobs, recorder, filtering, similarity, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GenerationService(RetryService retry, TemplateService templates,
                             VersionStore versions, EventGuard guard, GenerationJobStore jobs,
                             LlmCallRecorder recorder, com.newvent.filtering.FilteringPolicy filtering,
                             SimilarityService similarity, LlmCallGateway gateway) {
        this.gateway = gateway;
        this.filtering = filtering;
        this.similarity = similarity;
        this.retry = retry;
        this.templates = templates;
        this.versions = versions;
        this.guard = guard;
        this.jobs = jobs;
        this.recorder = recorder;
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    // ── 시작 ──────────────────────────────────────────────────────

    /** 시작 결과. 거부도 정상 흐름이라 예외로 던지지 않는다 */
    public sealed interface StartResult {
        record Started(GenerationJob job) implements StartResult {}

        /** 요청 자체가 잘못됐다. 컨트롤러가 GenerationException 으로 바꿔 던진다 */
        record Rejected(ErrorCode errorCode) implements StartResult {}

        /** 이미 돌고 있다 — 409. 진행 중인 작업을 같이 준다 */
        record AlreadyRunning(GenerationJob job) implements StartResult {}
    }

    /**
     * 검사만 하고 바로 돌아온다. **실제 생성은 다른 스레드에서 돈다.**
     *
     * ★ 검사 순서가 중요하다 — 자리를 잡기 전에 거를 것을 다 거른다.
     *   자리를 먼저 잡으면 거부된 요청이 그 이벤트의 생성을 잠시 막는다.
     *   상한(⑤)은 더 나쁘다 — 예외로 나가서 자리를 **영구히** 붙잡는다. ④ 주석 참고.
     */
    public StartResult start(GenerateCommand cmd) {
        // ① 이벤트를 건드려도 되는가
        Optional<ErrorCode> blocked = guard.rejectReason(cmd.eventId());
        if (blocked.isPresent()) {
            return new StartResult.Rejected(blocked.get());
        }

        // ② 요청문을 조용히 버리지 않는다
        //
        // ★ 경로별 검사보다 먼저 본다. 템플릿 코드가 멀쩡한지와 무관하게
        //   이 조합 자체가 틀렸다. 뒤에 두면 "없는 템플릿 + 요청문" 이
        //   TEMPLATE_NOT_FOUND 로 나가서 진짜 원인이 가려진다.
        if (cmd.discardsRequestText()) {
            log.warn("생성 요청이 모순입니다 (event={}) — {} 템플릿 '{}' 로 가면서 "
                    + "요청문 {}자를 들고 있습니다. 그대로 두면 모델을 안 부른 채 "
                    + "요청문만 버려지고 DONE 으로 끝납니다. "
                    + "AI 생성이면 templateCode:\"\" 를, 템플릿이면 requestText 를 비워 보내야 합니다.",
                    cmd.eventId(),
                    cmd.templateFromEvent() ? "templateCode 를 보내지 않아 이벤트에 붙은" : "명시한",
                    cmd.templateCode(),
                    cmd.requestText().strip().length());
            return new StartResult.Rejected(GenerationErrorCode.AMBIGUOUS_GENERATION);
        }

        // ③ 경로별 검사
        if (cmd.hasTemplate()) {

            if (!templates.exists(cmd.templateCode())) {
                log.warn("없는 템플릿 코드로 생성 요청 (event={}, code={}). "
                        + "events.template_id 와 템플릿 저장소가 어긋났을 수 있습니다.",
                        cmd.eventId(), cmd.templateCode());
                return new StartResult.Rejected(GenerationErrorCode.TEMPLATE_NOT_FOUND);
            }
        } else {
            // 입력 필터  — 백지 생성일 때만. 템플릿 경로는 요청문이 필요 없다
            Optional<GenerationErrorCode> bad = RequestFilter.reject(cmd.requestText());
            if (bad.isPresent()) {
                return new StartResult.Rejected(bad.get());
            }
        }

        // ③ 이미 돌고 있나 — **자리를 잡지 않고** 먼저 본다
        var input = cmd.hasTemplate()
                ? com.newvent.filtering.FilteringPolicy.Result.accepted("")
                : filtering.prepare(cmd.requestText(), cmd.privacyConfirmationJobId(), cmd.eventId(),
                        "generate", null, jobs, cmd.privacyConfirmed(), cmd.title());
        if (input.error() != null) return new StartResult.Rejected(input.error());
        if (cmd.hasTemplate() && (cmd.privacyConfirmationJobId() != null || cmd.privacyConfirmed())) {
            return new StartResult.Rejected(com.newvent.filtering.FilteringErrorCode.PRIVACY_CONFIRMATION_NOT_FOUND);
        }
        GenerateCommand safeCommand = new GenerateCommand(cmd.eventId(), cmd.templateCode(),
                cmd.title(), cmd.period(), cmd.ctaUrl(), input.text());
        Optional<GenerationJob> already = jobs.ofEvent(cmd.eventId());
        if (already.isPresent()) {
            return new StartResult.AlreadyRunning(already.get());
        }

        // ⑤ 하루 상한 — 백지 경로만. 템플릿 경로는 모델을 아예 안 부른다

        if (!cmd.hasTemplate() && input.question() == null) {
            retry.reserve();
        }

        // ⑥ 자리 잡기

        Optional<GenerationJob> slot = jobs.start(cmd.eventId());
        if (slot.isEmpty()) {
            // ★ 반대로 그 사이에 **끝났을** 수도 있다. orElseThrow 면 NoSuchElementException → 500 이다
            return jobs.ofEvent(cmd.eventId())
                    .<StartResult>map(StartResult.AlreadyRunning::new)
                    .orElseGet(() -> new StartResult.Rejected(
                            GenerationErrorCode.ALREADY_GENERATING));
        }

        GenerationJob job = slot.get();
        job.privacyRequest("generate", null);
        job.privacyConfirmation(input.fingerprint(), input.privacyTypes());
        if (input.question() != null) {
            job.askBack(input.question());
            jobs.finish(job);
        } else {
            worker.submit(() -> runSafely(job, safeCommand));
        }
        return new StartResult.Started(job);
    }

    /** 중단 요청 — 즉시 멈추지 않는다. 단계 사이에서 멈춘다 (REQ-LLM-34) */
    public boolean cancel(GenerationJob job) {
        if (job.done()) return false;
        job.requestCancel();
        return true;
    }

    // ── 실행 ──────────────────────────────────────────────────────

    /**
     * ★ 여기서 모든 예외를 잡는다.
     */
    private void runSafely(GenerationJob job, GenerateCommand cmd) {
        try {
            run(job, cmd);
        } catch (RetryService.Aborted e) {
            // ★ 터진 시도의 실패 행은 문(LlmCallGateway)이 이미 남겼다.
            //   그 앞의 시도들은 fromBlank 가 chunkIds 를 달아 이미 남겼다 — 여기서 또 남기면 유니크 위반이다.
            //   실패한 시도 자체는 Gateway가 별도 저장했으므로, 여기서는 RAG 정보만 갱신한다.
            log.warn("생성 실패 — {}차 시도에서 모델 호출 (event={})", e.attempt(), cmd.eventId(), e);
            // ★ ctxOf() 는 attemptNo 가 항상 1이라, 2차 이후 실패는 실제 시도 행을 못 찾는다.
            //   e.attempt() 로 실패한 시도 번호를 넘겨 그 행의 RAG 정보를 갱신한다.
            recorder.updateRagInfo(ctxOf(job, cmd).attempt(e.attempt()), ragChunkIds);
            job.fail("페이지 생성 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (LlmCallException e) {
            // 연결 끊김 · 타임아웃 · 모델 서버 down. 프롬프트를 고쳐도 안 고쳐진다
            log.warn("생성 실패 — 모델 호출 (event={})", cmd.eventId(), e);
            job.fail("페이지 생성 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (Exception e) {
            log.error("생성 실패 — 예상치 못한 오류 (event={})", cmd.eventId(), e);
            job.fail("페이지를 만드는 중 문제가 생겼습니다. 잠시 후 다시 시도해 주세요.");
        } finally {
            // ★ 반드시 비운다. 안 비우면 그 이벤트는 다음 생성을 영원히 못 한다
            jobs.finish(job);
        }
    }

    private void run(GenerationJob job, GenerateCommand cmd) {
        job.to(GenerationJob.Phase.PREPARING);
        if (job.checkCancelled()) return;

        String html = cmd.hasTemplate()
                ? fromTemplate(job, cmd)
                : fromBlank(job, cmd);

        if (html == null) return;
        if (job.checkCancelled()) return;

        job.to(GenerationJob.Phase.SAVING);

        // ★ sourceVersionId = null 이다. 생성은 고친 원본이 없다 — 두 번째 생성이어도 그렇다.
        //   기준이 된 버전을 가리키는 건 수정(EditService)의 일이다.
        VersionStore.Saved saved = versions.save(cmd.eventId(), html, null);

        // ★ "템플릿 T1" / "백지 생성" 은 event_versions 에 넣을 컬럼이 없어 로그로만 남긴다
        log.info("버전 저장 (event={}, versionId={}, v{}) — {}",
                cmd.eventId(), saved.versionId(), saved.versionNo(),
                cmd.hasTemplate() ? "템플릿 " + cmd.templateCode() : "백지 생성");

        // ★★ 순서가 중요하다 — versionId 를 먼저 넣고 DONE 을 나중에 넣는다
        //   폴링은 다른 스레드(HTTP)에서 읽는다. 반대로 하면 프론트가
        //   phase == DONE 을 보고 versionId 를 읽었는데 null 인 순간이 생긴다.
        job.versionId(saved.versionId());
        job.to(GenerationJob.Phase.DONE);
    }

    // ── 경로 ① 템플릿 ────────────────────────────────────────────

    /**
     * 모델을 안 부른다. 재정화 → 슬롯 비우기까지가 전부다.
     *
     * ★ 슬롯을 비운 상태로 저장한다. 값은 보여줄 때 채운다.
     *   저장할 때 채우면 그 날짜가 굳어서, 이벤트에서 기간만 고쳐도 화면이 안 바뀐다.
     */
    private String fromTemplate(GenerationJob job, GenerateCommand cmd) {
        job.to(GenerationJob.Phase.VALIDATING);
        return templates.initialHtml(cmd.templateCode());
    }

    // ── 경로 ② 백지 ──────────────────────────────────────────────

    private String fromBlank(GenerationJob job, GenerateCommand cmd) {
        job.to(GenerationJob.Phase.CALLING);

        // ★ b3: RAG 참고 예시 검색. searchRagChunks 안에서 예외를 다 잡으므로 여기서 터지지 않는다.
        //   빈 리스트면 ragChunkIds=null → 로그에 rag_used=false 로 남는다.
        List<RagChunk> ragChunks = searchRagChunks(cmd);
        ragChunkIds = chunkIdsOf(ragChunks);

        RetryService.Result res;
        try {
            res = retry.run(
                    ctxOf(job, cmd),
                    PromptBuilder.generate(cmd.requestText()),
                    userPromptWithRag(cmd, ragChunks),
                    HtmlPolicy.generation(cmd.requestText(), cmd.title(), cmd.period()));
        } catch (RetryService.Aborted e) {
            // ★ 터진 시도 앞부분을 chunkIds 와 함께 먼저 기록하고 다시 던진다.
            //   runSafely 쪽 기록을 지웠으므로 중복 기록이 아니다.
            recorder.recordAttempts(ctxOf(job, cmd), e.partial(), ragChunkIds);
            throw e;
        }

        // ★ 성공이든 실패든 시도 전부를 남긴다. 저장·취소보다 먼저 — 취소돼도 쓴 토큰은 쓴 것이다
        recorder.recordAttempts(ctxOf(job, cmd), res, ragChunkIds);

        job.attempt(res.attempts());
        if (job.checkCancelled()) return null;

        job.to(GenerationJob.Phase.VALIDATING);

        if (!res.ok()) {
            // ★ 여기서 기본 템플릿을 끼워 넣지 않는다 (REQ-LLM-44 뒤집기)
            log.info("생성 실패 — {}회 시도, 마지막 사유 {}",
                    res.attempts(), res.lastFailures());
            job.fail("요청을 반영한 페이지를 만들지 못했습니다. "
                    + "요청을 조금 더 구체적으로 적어 다시 시도해 주세요.");
            return null;
        }
        checkFormValues(cmd, res.html());
        // ★ 저장 직전에 껍데기를 보장한다 — 래퍼 · 유의사항.
        //   백지는 템플릿이 없으므로 테마를 고를 근거가 없다 → null.
        //   event.css 의 :root 기본값이 쓰인다. 무스타일이 아니다.
        // ★ 동작(참여 · 타이머 · 이동 버튼)을 심는다. 생성 정화가 모델이 쓴 data-behavior 를 지운 뒤라 서버 것만 남는다
        return PageShell.plant(plantBehaviors(cmd, plantPeriodSlot(res.html())), null);
    }

    /**
     * 이벤트 값과 어긋나는지 본다.
     *
     *   **이 로그가 곧 측정이다.** 한 주 동안 얼마나 자주 나는지 보고,
     *   흔하면 프롬프트를 고치고 드물면 검증으로 올린다.
     *   올리는 건 HtmlPolicy.generation() 에 한 줄 더하는 일이다.
     */
    private void checkFormValues(GenerateCommand cmd, String html) {
        List<String> dates = FormValueCheck.leakedDates(html);
        if (dates.isEmpty()) return;

        // ★ WARN 이다. 찾으려면 눈에 띄어야 한다
        log.warn("REQ-LLM-41 — 본문에 지어낸 날짜 {}개 (event={}): {}. "
                + "기간은 슬롯으로만 들어가야 합니다. 프롬프트에는 날짜를 주지 않았습니다.",
                dates.size(), cmd.eventId(), dates);
    }

    private static final Failure PLAN_TRUNCATED =
            Failure.of(FailureCode.TRUNCATED, "요청이 너무 복잡해 동작 배치 출력이 잘렸습니다.");
    private static final Failure PLAN_UNPARSABLE =
            Failure.of(FailureCode.ROUTER_PARSE, "동작 배치 출력이 규격에 맞지 않습니다.");

    /**
     * 백지 결과에 동작을 심는다 — 동작 배치기(LLM)가 JSON 으로 고르고, 서버가 검증해서 심는다.
     *
     * ★ 모델은 HTML 을 쓰지 않는다. "무엇을 어디에" 만 고른다 — BehaviorPlanter.decide 가 검증한다
     * ★ 배치기가 실패해도 생성은 실패하지 않는다 — 기본 배치(plantGenerated)로 간다.
     *   잘림 · 규격 밖 출력 · 호출 실패 · 하루 상한 모두 같다. 재시도는 없다 (모델 호출 1회)
     * ★ 새 묶음이다 — jobId 묶음은 HTML 생성 재시도가 쓴다. 같이 쓰면 (request_id, attempt_no) 유니크에 걸린다
     */
    private String plantBehaviors(GenerateCommand cmd, String html) {
        if (gateway == null) return BehaviorPlanter.plantGenerated(html);

        Document doc = Jsoup.parseBodyFragment(html);
        List<Block> present = new java.util.ArrayList<>();
        for (Block b : Block.values()) if (doc.body().selectFirst(b.selector()) != null) present.add(b);

        try {
            LlmCallContext ctx = LlmCallContext.newGroup(cmd.eventId());
            LlmClient.Response res = gateway.call(ctx, LlmClient.Request.router(
                    PromptBuilder.behaviorPlanner(),
                    PromptBuilder.behaviorPlannerUser(RequestFilter.clean(cmd.requestText()), present)));
            Optional<List<BehaviorPlanter.Placement>> placements =
                    res.truncated() ? Optional.empty() : BehaviorPlanter.decide(res.content(), doc.body());
            recorder.recordSingle(ctx, res, placements.isPresent() ? List.of()
                    : List.of(res.truncated() ? PLAN_TRUNCATED : PLAN_UNPARSABLE));
            if (placements.isPresent()) {
                log.info("동작 배치 (event={}) — {}", cmd.eventId(), placements.get().stream()
                        .map(p -> p.behavior().key() + "@" + p.block().key()).toList());
                return BehaviorPlanter.apply(html, placements.get());
            }
            log.info("동작 배치 출력을 읽지 못해 기본 배치로 (event={})", cmd.eventId());
        } catch (RuntimeException e) {
            log.warn("동작 배치 실패 — 기본 배치로 (event={})", cmd.eventId(), e);
        }
        return BehaviorPlanter.plantGenerated(html);
    }

    /**
     * 백지 결과에 기간 슬롯을 심는다.
     */
    private String plantPeriodSlot(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);

        if (!doc.body().select(Slot.PERIOD.selector()).isEmpty()) {
            return doc.body().html();      // 이미 있으면 두 번 심지 않는다
        }

        Element hero = doc.body().selectFirst(Block.HERO.selector());
        Element host = (hero != null) ? hero : doc.body();

        // ★ 내용은 비워 둔다. 값은 보여줄 때 Slots.fill() 이 채운다
        host.appendElement("p").attr("data-slot", Slot.PERIOD.key());

        return doc.body().html();
    }

    /**
     * 이 작업의 호출 꼬리표.
     *
     * ★ requestId = jobId 다. 작업 하나가 로그에서 재시도 묶음 하나가 된다.
     *   {@code (request_id, attempt_no)} 가 유니크라서 같은 작업을 두 번 기록하면 저장이 터진다.
     */
    private static LlmCallContext ctxOf(GenerationJob job, GenerateCommand cmd) {
        return LlmCallContext.of(cmd.eventId(), job.jobId());
    }

    // ── 프롬프트 ──────────────────────────────────────────────────

    /**
     * 모델에게 보낼 사용자 메시지.
     *
     * ★ 기간을 **주지 않는다.**
     *   기간은 슬롯 하나로만 존재해야 한다.
     *
     * ★ 참여 링크도 안 준다. 같은 이유이고, CTA 는 문구만 만들면 된다.
     */
    private String userPrompt(GenerateCommand cmd) {
        StringBuilder s = new StringBuilder();
        if (cmd.title() != null && !cmd.title().isBlank()) {
            s.append("이벤트 제목: ").append(cmd.title().strip()).append("\n\n");
        }
        s.append(RequestFilter.clean(cmd.requestText()));
        return s.toString();
    }

    /**
     * RAG 참고 예시 검색. 읽기 전용이라 생성 트랜잭션과 무관하게 돈다.
     *
     * ★ try-catch 로 감싼 이유 — 검색이 터져도 생성은 계속돼야 한다.
     *   임베딩 키 없음·DB 순간 장애가 "페이지 생성 실패" 가 되면 안 된다.
     */
    private List<RagChunk> searchRagChunks(GenerateCommand cmd) {
        if (cmd.requestText() == null || cmd.requestText().isBlank()) {
            return List.of();
        }
        try {
            return similarity.search(cmd.eventId(), cmd.requestText().strip(), RagConstants.DEFAULT_TOP_K);
        } catch (RuntimeException e) {
            log.warn("RAG 검색 실패 — RAG 없이 생성한다 (event={})", cmd.eventId(), e);
            return List.of();
        }
    }

    /**
     * 사용자 프롬프트 + RAG 참고 예시 섹션.
     *
     * ★ 말투는 SimilarityService.recommendPrompts 와 같은 꼴 — "가이드 (예시: 내용)".
     * ★ "그대로 베끼지 말라" 를 박은 이유 — 타 이벤트 문구를 토씨까지 복사하면 표절 시비가 난다.
     */
    private String userPromptWithRag(GenerateCommand cmd, List<RagChunk> chunks) {
        String base = userPrompt(cmd);
        if (chunks.isEmpty()) {
            return base;
        }
        StringBuilder s = new StringBuilder(base);
        s.append("\n\n## 참고 예시 (과거 유사 이벤트에서 가져온 것. 그대로 베끼지 말고 말투·구성의 참고로만 쓴다)\n");
        for (RagChunk c : chunks) {
            Block block = Block.find(c.getBlockKey()).orElse(null);
            String guide = block != null ? block.shape() : "";
            s.append("- [").append(c.getBlockKey()).append("] ");
            if (!guide.isBlank()) {
                s.append(guide).append(' ');
            }
            s.append("(예시: ").append(c.getContent()).append(")\n");
        }
        return s.toString();
    }

    /**
     * LlmCallLog.chunk_ids(TEXT)에 넣을 문자열. "41,42" 꼴.
     *
     * ★ 빈 리스트면 null — recordAttempts 가 null·blank 면 markRagUsed 를 건너뛴다.
     */
    private static String chunkIdsOf(List<RagChunk> chunks) {
        if (chunks.isEmpty()) {
            return null;
        }
        StringBuilder s = new StringBuilder();
        for (RagChunk c : chunks) {
            if (s.length() > 0) s.append(',');
            s.append(c.getId());
        }
        return s.toString();
    }

    // ── 보여주기 ──────────────────────────────────────────────────

    /**
     * 미리보기 한 장. <b>HTML 과 그 HTML 이 어느 버전인지를 같이 준다.</b>
     * ★ versionId 를 같이 주는 이유
     *   자동 저장 버전은 checkpoint = false 라서 저장 지점 목록 API 에 안 나온다.
     *   프론트가 "지금 화면이 어느 버전인가" 를 알 수 있는 경로가
     *   생성 폴링 응답과 이 미리보기 응답뿐이다. 이후 수정 요청의 기준이 된다.
     */
    public record Rendered(Long versionId, int versionNo, String html) {}

    /**
     * 저장된 HTML 에 이벤트 값을 채워 돌려준다. **미리보기·게시 응답에서 부른다.**
     *
     * ★ 저장 경로에서 부르면 안 된다. 값이 박혀서 들어가고 clear() 한 이유가 없어진다.
     */
    public Optional<Rendered> render(GenerateCommand cmd) {
        return versions.latest(cmd.eventId())
                .map(v -> new Rendered(v.versionId(), v.versionNo(),
                        Slots.fill(v.html(), cmd.period(), cmd.ctaUrl())));
    }
}
