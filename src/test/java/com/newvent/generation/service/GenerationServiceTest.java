package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.LlmDailyLimitExceededException;
import com.newvent.infra.llm.LlmCallContext;
import com.newvent.infra.llm.LlmCallGateway;
import com.newvent.infra.llm.LlmClient;
import com.newvent.registry.Block;
import com.newvent.registry.BlockValidator;
import com.newvent.registry.Slot;
import com.newvent.registry.Slots;

/**
 * 생성 흐름. **스프링도 DB 도 모델도 안 띄운다.**
 *
 * ★ RetryService 를 상속으로 바꿔치기한다
 *
 * ★ TemplateService 는 진짜를 쓴다
 *   ResourceTemplateLoader 가 classpath 에서 읽으므로 DB 가 필요 없고,
 *   **진짜 템플릿으로 슬롯 비우기까지 확인**할 수 있다.
 */
class GenerationServiceTest {

    /** 모델을 부르지 않는 가짜. 무엇을 돌려줄지 테스트가 정한다 */
    static final class FakeRetry extends RetryService {
        private RetryService.Result next;
        private String lastPrompt;
        private final AtomicInteger calls = new AtomicInteger();

        // ★ 기본은 Direct — reserve() 가 no-op 이라 상한 검사가 테스트에 끼어들지 않는다
        FakeRetry() { this(new LlmCallGateway.Direct(null)); }

        /** 상한 경로를 보려면 문을 직접 준다 */
        FakeRetry(LlmCallGateway gateway) { super(gateway, 0); }

        void willReturn(RetryService.Result r) { this.next = r; }
        int calls() { return calls.get(); }

        // ★ ctx 를 받는 쪽을 덮어야 한다. GenerationService 는 이 쪽만 부른다
        @Override
        public RetryService.Result run(LlmCallContext ctx, String system, String user, HtmlPolicy policy) {
            calls.incrementAndGet();
            lastPrompt = user;
            return next;
        }
    }

    /** 성공 결과 하나 — 검증을 통과한 상태의 HTML 을 흉내 낸다 */
    private static RetryService.Result ok(String html) {
        return new RetryService.Result(true, html, List.of());
    }

    private static RetryService.Result fail() {
        return new RetryService.Result(false, null, List.of());
    }

    private static final String GENERATED = """
            <section data-block="hero"><h1>여름 데이터 대방출</h1><p>걱정 없이</p></section>
            <section data-block="benefits"><ul><li>데이터 10GB</li><li>쿠폰</li></ul></section>
            <section data-block="cta"><a href="#" class="btn">참여하기</a></section>
            """;

    private FakeRetry retry;
    private GenerationJobStore jobs;
    private VersionStore versions;
    private GenerationService service;
    private EventGuard guard;

    @BeforeEach
    void setUp() {
        retry = new FakeRetry();
        jobs = new GenerationJobStore();
        versions = new VersionStore.InMemory();
        guard = new EventGuard.Open();
        service = new GenerationService(
                retry,
                new TemplateService(new com.newvent.generation.service.ResourceTemplateLoader()),
                versions, guard, jobs, LlmCallRecorder.none());
    }

    /** 워커 스레드가 끝날 때까지 기다린다. 2초면 충분하다 — 모델을 안 부르므로 */
    private static GenerationJob await(GenerationJob job) {
        long deadline = System.currentTimeMillis() + 2000;
        while (!job.done() && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(job.done(),
                "2초 안에 안 끝났습니다. 현재 단계=" + job.phase()
                + " — 워커 스레드가 막혔거나 예외가 새어 나갔습니다.");
        return job;
    }

    private static GenerationJob started(GenerationService.StartResult r) {
        assertInstanceOf(GenerationService.StartResult.Started.class, r,
                "시작되지 않았습니다: " + r);
        return ((GenerationService.StartResult.Started) r).job();
    }

    /** 저장된 마지막 HTML. latest() 가 Snapshot 을 주므로 html() 로 꺼낸다 */
    private String savedHtml(long eventId) {
        return versions.latest(eventId).orElseThrow().html();
    }

    private GenerateCommand blank(long id, String text) {
        return new GenerateCommand(id, null, false, "여름 이벤트", "2026.07.01 ~ 07.31", null, text);
    }

    private GenerateCommand template(long id, String code) {
        return new GenerateCommand(id, code, false, "한가위 이벤트", "2026.09.20 ~ 10.05",
                "https://event.example.com/a", null);
    }

    /**
     * 템플릿과 요청문을 함께 들고 있는 꼴. **요청문이 버려질 수 있는 조합이다.**
     *
     * @param fromEvent 코드가 요청이 아니라 이벤트에서 왔나 (폴백이 걸린 꼴)
     */
    private GenerateCommand both(long id, String code, String text, boolean fromEvent) {
        return new GenerateCommand(id, code, fromEvent, "한가위 이벤트", "2026.09.20 ~ 10.05",
                null, text);
    }

    /**
     * 거부 사유를 꺼낸다.
     *
     * ★ GenerationErrorCode 로 캐스팅하지 않는다. 다른 도메인 코드가 올라오면
     *   ClassCastException 이 나서 "무엇이 왔는지" 가 가려진다. assertEquals 가
     *   실제로 온 코드를 찍게 둔다.
     */
    private static com.newvent.common.exception.code.ErrorCode rejectedWith(
            GenerationService.StartResult r) {
        assertInstanceOf(GenerationService.StartResult.Rejected.class, r,
                "거부되지 않았습니다: " + r);
        return ((GenerationService.StartResult.Rejected) r).errorCode();
    }

    // ── 경로 ① 템플릿 ────────────────────────────────────────────

    @Test
    void 개인정보_확인후에만_생성하고_원문을_전달한다() {
        String request = "데이터 10GB 이벤트, 연락처 a@example.com";
        GenerationJob question = started(service.start(blank(1L, request)));
        assertEquals(GenerationJob.Phase.ASK_BACK, question.phase());
        assertTrue(question.privacyConfirmationRequired());
        assertEquals(0, retry.calls());
        assertTrue(versions.latest(1L).isEmpty());
        retry.willReturn(ok(BlockValidator.sanitizeGenerated(GENERATED)));
        GenerationJob reply = await(started(service.start(new GenerateCommand(
                1L, null, "여름 이벤트", null, null, request, question.jobId(), true))));
        assertEquals(GenerationJob.Phase.DONE, reply.phase());
        assertTrue(retry.lastPrompt.contains("a@example.com"));
    }

    @Test
    void 금지표현_목록에_의한_차단은_없다() {
        retry.willReturn(ok(BlockValidator.sanitizeGenerated(GENERATED)));
        assertEquals(GenerationJob.Phase.DONE, await(started(service.start(blank(1L, "씨발 문구 수정")))).phase());
        assertEquals(1, retry.calls());
    }

    @Test
    @DisplayName("★ 템플릿 경로는 모델을 아예 안 부른다")
    void 템플릿은_모델을_안_부른다() {
        GenerationJob job = await(started(
                service.start(template(1L, "holiday_gift"))));

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertEquals(0, retry.calls(),
                "템플릿 경로에서 모델을 불렀습니다. 파일에서 읽어 슬롯만 비우면 되는 경로입니다.");
    }

    @Test
    @DisplayName("★ 템플릿 저장본은 슬롯이 비어 있다 — 날짜가 굳으면 안 된다")
    void 템플릿_슬롯이_비어있다() {
        await(started(service.start(template(1L, "holiday_gift"))));

        String saved = savedHtml(1L);
        var doc = Jsoup.parseBodyFragment(saved);

        assertEquals("", doc.body().select(Slot.PERIOD.selector()).text(),
                "기간이 안 비워졌습니다. 그 날짜가 굳어서 폼을 고쳐도 화면이 안 바뀝니다.");
        assertFalse(saved.contains("2026.09.10"),
                "템플릿의 데모 날짜가 그대로 저장됐습니다.");

        var cta = doc.body().selectFirst(Slot.CTA_LINK.selector());
        assertNotNull(cta, "cta-link 슬롯이 사라졌습니다.");
        assertFalse(cta.text().isBlank(),
                "CTA 문구가 지워졌습니다. cta-link 는 속성만 비워야 합니다.");
    }

    @Test
    @DisplayName("템플릿 경로는 요청문이 없어도 된다")
    void 템플릿은_요청문이_필요없다() {
        assertInstanceOf(GenerationService.StartResult.Started.class,
                service.start(template(1L, "sports_cheer")),
                "템플릿을 골랐는데 요청문이 없다고 거부했습니다.");
    }

    @Test
    @DisplayName("★ 없는 템플릿 코드는 시작 전에 거부한다 — 워커에서 터지면 원인을 못 본다")
    void 없는_템플릿() {
        var result = service.start(template(1L, "template_99_없음"));

        assertInstanceOf(GenerationService.StartResult.Rejected.class, result,
                "시작돼 버렸습니다. 워커 스레드에서 IllegalArgumentException 이 나고, "
                + "관리자는 '문제가 생겼습니다' 만 봅니다. 다시 눌러도 영원히 같습니다.");
        assertEquals(GenerationErrorCode.TEMPLATE_NOT_FOUND,
                ((GenerationService.StartResult.Rejected) result).errorCode());

        assertTrue(jobs.ofEvent(1L).isEmpty(),
                "거부됐는데 자리를 잡았습니다. 그 이벤트의 다음 생성이 잠시 막힙니다.");
    }

    // ── 경로가 섞인 요청 ─────────────────────────────────────────
    //
    // ★ 여기가 조용한 사고였던 자리다
    //   템플릿 경로는 모델을 안 부르므로, 요청문을 버려도 아무 데서도 터지지 않고
    //   202 → DONE 으로 성공처럼 끝났다. 관리자는 AI 가 반영한 줄 알고,
    //   일일 상한에도 안 잡히고, llm_call_logs 에도 행이 없어 추적 단서가 없었다.

    @Test
    @DisplayName("★ 템플릿 코드와 요청문을 함께 보내면 거부한다 — 요청문을 조용히 버리지 않는다")
    void 템플릿과_요청문을_함께_보내면_거부한다() {
        var r = service.start(both(1L, "holiday_gift", "데이터 3GB 주는 이벤트", false));

        assertEquals(GenerationErrorCode.AMBIGUOUS_GENERATION, rejectedWith(r),
                "요청문이 있는데 템플릿 경로로 갔습니다. 모델을 안 부르고 요청문만 버린 뒤 "
                + "DONE 으로 끝나므로, 관리자는 AI 가 반영한 줄 압니다.");
        assertEquals(0, retry.calls(), "거부했는데 모델을 불렀습니다.");
    }

    @Test
    @DisplayName("★ 폴백으로 템플릿에 갈 때도 거부한다 — templateCode 를 생략한 꼴")
    void 폴백으로_템플릿에_가도_거부한다() {
        // ★ 이게 실제로 사람을 속인 조합이다. 프론트가 "AI로 만들기" 에서
        //   templateCode 를 안 보내면, 이벤트에 붙은 템플릿으로 폴백이 걸린다.
        var r = service.start(both(1L, "holiday_gift", "데이터 3GB 주는 이벤트", true));

        assertEquals(GenerationErrorCode.AMBIGUOUS_GENERATION, rejectedWith(r),
                "templateCode 를 생략한 AI 생성 요청이 템플릿 페이지를 만들어 버렸습니다.");
    }

    @Test
    @DisplayName("거부된 요청은 자리를 잡지 않는다 — 다음 생성이 막히면 안 된다")
    void 모순_요청은_자리를_잡지_않는다() {
        service.start(both(1L, "holiday_gift", "데이터 3GB", true));

        assertTrue(jobs.ofEvent(1L).isEmpty(),
                "거부됐는데 자리를 잡았습니다. 그 이벤트의 다음 생성이 잠시 막힙니다.");

        // ★ 바로 이어서 제대로 된 요청이 통과해야 한다. 거부가 잔상을 남기면 안 된다
        retry.willReturn(ok(GENERATED));
        assertInstanceOf(GenerationService.StartResult.Started.class,
                service.start(blank(1L, "데이터 3GB 주는 이벤트")),
                "앞의 거부가 자리를 붙잡고 있습니다.");
    }

    @Test
    @DisplayName("템플릿만 보내면 그대로 된다 — 거부가 템플릿 경로를 막아선 안 된다")
    void 템플릿만_보내면_통과한다() {
        GenerationJob job = await(started(service.start(template(1L, "holiday_gift"))));

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertEquals(0, retry.calls());
    }

    @Test
    @DisplayName("공백만 있는 요청문은 없는 것으로 본다 — EMPTY_REQUEST 와 기준이 같아야 한다")
    void 공백_요청문은_템플릿을_막지_않는다() {
        // ★ 기준이 어긋나면 "   " 가 템플릿 경로에서는 모순으로 거부되고
        //   백지 경로에서는 EMPTY_REQUEST 로 거부되어, 같은 입력에 다른 코드가 나간다.
        GenerationJob job = await(started(service.start(both(1L, "holiday_gift", "   \n ", false))));

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
    }

    // ── 경로 ② 백지 ──────────────────────────────────────────────

    @Test
    @DisplayName("★ 백지 결과에 기간 슬롯이 심어진다 — 안 하면 기간이 안 나온다")
    void 백지에_기간슬롯을_심는다() {
        retry.willReturn(ok(BlockValidator.sanitizeGenerated(GENERATED)));

        GenerationJob job = await(started(service.start(blank(1L, "여름 데이터 이벤트"))));
        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());

        String saved = savedHtml(1L);
        assertEquals(java.util.Set.of("period"), Slots.keysOf(saved),
                "기간 슬롯이 없습니다. 백지로 만든 페이지에는 기간이 영영 안 나옵니다.");

        var hero = Jsoup.parseBodyFragment(saved).body().selectFirst(Block.HERO.selector());
        assertNotNull(hero);
        assertFalse(hero.select(Slot.PERIOD.selector()).isEmpty(),
                "기간 슬롯이 hero 밖에 붙었습니다. 템플릿 5종과 같은 자리여야 합니다.");
    }

    @Test
    @DisplayName("슬롯은 비워서 저장하고, 보여줄 때 채운다")
    void 채우기는_보여줄때() {
        retry.willReturn(ok(BlockValidator.sanitizeGenerated(GENERATED)));
        GenerateCommand cmd = blank(1L, "여름 데이터 이벤트");
        await(started(service.start(cmd)));

        assertFalse(savedHtml(1L).contains("2026.07.01"),
                "저장본에 날짜가 박혔습니다. 비워서 저장해야 합니다.");

        String rendered = service.render(cmd).orElseThrow().html();
        assertTrue(rendered.contains("2026.07.01 ~ 07.31"),
                "보여줄 때 기간이 안 채워졌습니다: " + rendered);
    }

    @Test
    @DisplayName("★ 다 실패하면 실패로 끝난다 — 기본 템플릿을 끼워 넣지 않는다")
    void 폴백이_없다() {
        retry.willReturn(fail());

        GenerationJob job = await(started(service.start(blank(1L, "여름 데이터 이벤트"))));

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertTrue(versions.latest(1L).isEmpty(),
                "실패했는데 버전이 저장됐습니다. 관리자가 요청하지 않은 페이지가 "
                + "조용히 들어갑니다 (REQ-LLM-44 뒤집기).");
        assertNotNull(job.message(), "실패 사유가 없습니다.");
        assertFalse(job.message().toLowerCase().contains("exception"),
                "내부 오류 원문이 노출됐습니다 (REQ-LLM-36): " + job.message());
    }

    @Test
    @DisplayName("예외가 나도 자리가 비워진다 — 안 비우면 그 이벤트는 영영 못 만든다")
    void 예외가_나도_자리는_비운다() {
        retry.willReturn(null);   // run() 이 null 을 돌려주면 NPE 가 난다

        GenerationJob job = await(started(service.start(blank(1L, "여름 데이터 이벤트"))));

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertTrue(jobs.ofEvent(1L).isEmpty(),
                "진행 중 목록에 남아 있습니다. finally 에서 finish() 를 안 부르면 이렇게 됩니다.");

        retry.willReturn(ok(BlockValidator.sanitizeGenerated(GENERATED)));
        assertInstanceOf(GenerationService.StartResult.Started.class,
                service.start(blank(1L, "다시 시도")),
                "실패 후 재시도가 막혔습니다.");
    }

    // ── 저장한 버전의 id 를 작업에 남기는가 ───────────────────────

    @Test
    @DisplayName("★ 끝난 작업은 저장한 버전의 id 를 들고 있다 — 폴링이 이걸 내려준다")
    void 완료된_작업은_버전_id_를_들고_있다() {
        retry.willReturn(ok(BlockValidator.sanitizeGenerated(GENERATED)));

        GenerationJob job = await(started(service.start(blank(1L, "여름 데이터 이벤트"))));

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertNotNull(job.versionId(),
                "DONE 인데 versionId 가 없습니다. 자동 저장 버전은 is_checkpoint=false 라서 "
                + "저장 지점 목록 API 에 안 나오고, 프론트가 그 id 를 알 경로가 "
                + "폴링 응답과 미리보기 응답뿐입니다.");
        assertEquals(versions.latest(1L).orElseThrow().versionId(), job.versionId(),
                "작업에 남은 id 가 실제 저장된 마지막 버전과 다릅니다.");
    }

    @Test
    @DisplayName("★ 템플릿 경로도 버전 id 를 남긴다 — 모델을 안 불러도 페이지는 생긴다")
    void 템플릿_경로도_버전_id_를_남긴다() {
        GenerationJob job = await(started(
                service.start(template(1L, "holiday_gift"))));

        assertNotNull(job.versionId(),
                "템플릿 경로에서 versionId 가 비었습니다. 이 경로도 버전 1행을 남깁니다.");
    }

    @Test
    @DisplayName("★ 실패로 끝난 작업의 versionId 는 null 이다 — 저장 단계에 못 갔다")
    void 실패한_작업은_버전_id_가_없다() {
        retry.willReturn(fail());

        GenerationJob job = await(started(service.start(blank(1L, "여름 데이터 이벤트"))));

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertNull(job.versionId(),
                "실패했는데 versionId 가 채워졌습니다. 프론트가 없는 버전을 "
                + "수정 기준으로 잡습니다.");
    }

    // ── 진입부 ────────────────────────────────────────────────────

    @Test
    @DisplayName("빈 요청은 자리를 잡기 전에 거부된다")
    void 빈_요청_거부() {
        var r = service.start(blank(1L, "   "));

        assertInstanceOf(GenerationService.StartResult.Rejected.class, r);
        assertEquals(GenerationErrorCode.EMPTY_REQUEST,
                ((GenerationService.StartResult.Rejected) r).errorCode());
        assertTrue(jobs.ofEvent(1L).isEmpty(),
                "거부된 요청이 자리를 잡았습니다. 그 이벤트의 다음 생성이 잠시 막힙니다.");
        assertEquals(0, retry.calls());
    }

    // ── 하루 상한과 자리 잡기의 순서 ──────────────────────────────

    /** 상한이 꽉 찬 문. 모델을 부르면 테스트가 깨진다 */
    private static LlmCallGateway full() {
        return new LlmCallGateway() {
            @Override
            public void reserve(int expectedCalls) {
                throw new LlmDailyLimitExceededException(200, 200);
            }

            @Override
            public LlmClient.Response call(LlmCallContext ctx, LlmClient.Request req) {
                throw new AssertionError("상한에 걸렸는데 모델을 불렀습니다.");
            }
        };
    }

    @Test
    @DisplayName("★ 하루 상한에 걸려도 자리를 잡지 않는다 — 잡으면 그 이벤트가 영구히 잠긴다")
    void 상한_초과는_자리를_잡지_않는다() {
        GenerationService s = new GenerationService(
                new FakeRetry(full()),
                new TemplateService(new com.newvent.generation.service.ResourceTemplateLoader()),
                versions, guard, jobs, LlmCallRecorder.none());

        assertThrows(LlmDailyLimitExceededException.class,
                () -> s.start(blank(1L, "여름 데이터 이벤트")));

        // ★ 자리를 잡았다면 worker.submit 이 안 됐으니 아무도 finish() 를 안 부른다.
        //   running 맵에 남아서 이 이벤트는 서버 재시작까지 AlreadyRunning 이 된다.
        assertTrue(jobs.ofEvent(1L).isEmpty(),
                "상한에 걸린 요청이 자리를 잡고 있습니다. "
                + "이 이벤트는 다음 생성을 영원히 못 합니다.");
    }

    @Test
    @DisplayName("★ 이미 돌고 있으면 상한을 보지 않는다 — 중복 요청은 모델을 0회 부른다")
    void 중복_요청은_상한을_보지_않는다() {
        // 상한이 꽉 차 있고 + 같은 이벤트가 이미 돌고 있다.
        // 답은 429 가 아니라 409 여야 한다 — 첫 요청은 살아 있다.
        GenerationJobStore store = new GenerationJobStore();
        GenerationJob held = store.start(1L).orElseThrow();

        GenerationService s = new GenerationService(
                new FakeRetry(full()),
                new TemplateService(new com.newvent.generation.service.ResourceTemplateLoader()),
                versions, guard, store, LlmCallRecorder.none());

        var r = s.start(blank(1L, "여름 데이터 이벤트"));

        var running = assertInstanceOf(GenerationService.StartResult.AlreadyRunning.class, r);
        assertEquals(held.jobId(), running.job().jobId());
    }

    @Test
    @DisplayName("★ EventGuard 가 막으면 시작조차 안 한다 (REQ-EVT-10)")
    void 종료된_이벤트() {
        GenerationService blocked = new GenerationService(
                retry,
                new TemplateService(new com.newvent.generation.service.ResourceTemplateLoader()),
                versions,
                id -> Optional.of(GenerationErrorCode.EMPTY_REQUEST),   // 아무 코드나 — 막히는지만 본다
                jobs, LlmCallRecorder.none());

        var r = blocked.start(blank(1L, "여름 데이터 이벤트"));

        assertInstanceOf(GenerationService.StartResult.Rejected.class, r);
        assertEquals(0, retry.calls());
    }

    @Test
    @DisplayName("같은 이벤트에 두 번째 요청은 409 로 돌아간다")
    void 중복_요청() {
        retry.willReturn(ok(BlockValidator.sanitizeGenerated(GENERATED)));

        // 워커가 하나라, 첫 작업이 도는 동안 두 번째를 바로 밀어 넣는다
        GenerationJobStore store = new GenerationJobStore();
        GenerationJob held = store.start(1L).orElseThrow();

        GenerationService s2 = new GenerationService(
                retry,
                new TemplateService(new com.newvent.generation.service.ResourceTemplateLoader()),
                versions, guard, store, LlmCallRecorder.none());

        var r = s2.start(blank(1L, "여름 데이터 이벤트"));

        assertInstanceOf(GenerationService.StartResult.AlreadyRunning.class, r);
        assertEquals(held.jobId(),
                ((GenerationService.StartResult.AlreadyRunning) r).job().jobId(),
                "돌고 있는 작업을 안 돌려줬습니다. 화면이 진행 상태를 이어 보여줄 수 없습니다.");
    }
}
