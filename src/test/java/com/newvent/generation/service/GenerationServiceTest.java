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
        private final AtomicInteger calls = new AtomicInteger();

        FakeRetry() { super(null, 0); }

        void willReturn(RetryService.Result r) { this.next = r; }
        int calls() { return calls.get(); }

        @Override
        public RetryService.Result run(String system, String user, HtmlPolicy policy) {
            calls.incrementAndGet();
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
                versions, guard, jobs);
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

    private GenerateCommand blank(long id, String text) {
        return new GenerateCommand(id, null, "여름 이벤트", "2026.07.01 ~ 07.31", null, text);
    }

    private GenerateCommand template(long id, String code) {
        return new GenerateCommand(id, code, "한가위 이벤트", "2026.09.20 ~ 10.05",
                "https://event.example.com/a", null);
    }

    // ── 경로 ① 템플릿 ────────────────────────────────────────────

    @Test
    @DisplayName("★ 템플릿 경로는 모델을 아예 안 부른다")
    void 템플릿은_모델을_안_부른다() {
        GenerationJob job = await(started(
                service.start(template(1L, "template_2_holiday_gift"))));

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertEquals(0, retry.calls(),
                "템플릿 경로에서 모델을 불렀습니다. 파일에서 읽어 슬롯만 비우면 되는 경로입니다.");
    }

    @Test
    @DisplayName("★ 템플릿 저장본은 슬롯이 비어 있다 — 날짜가 굳으면 안 된다")
    void 템플릿_슬롯이_비어있다() {
        await(started(service.start(template(1L, "template_2_holiday_gift"))));

        String saved = versions.latest(1L).orElseThrow();
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
                service.start(template(1L, "template_1_sports_cheer")),
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

    // ── 경로 ② 백지 ──────────────────────────────────────────────

    @Test
    @DisplayName("★ 백지 결과에 기간 슬롯이 심어진다 — 안 하면 기간이 안 나온다")
    void 백지에_기간슬롯을_심는다() {
        retry.willReturn(ok(BlockValidator.sanitizeGenerated(GENERATED)));

        GenerationJob job = await(started(service.start(blank(1L, "여름 데이터 이벤트"))));
        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());

        String saved = versions.latest(1L).orElseThrow();
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

        assertFalse(versions.latest(1L).orElseThrow().contains("2026.07.01"),
                "저장본에 날짜가 박혔습니다. 비워서 저장해야 합니다.");

        String rendered = service.render(cmd).orElseThrow();
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

    @Test
    @DisplayName("★ EventGuard 가 막으면 시작조차 안 한다 (REQ-EVT-10)")
    void 종료된_이벤트() {
        GenerationService blocked = new GenerationService(
                retry,
                new TemplateService(new com.newvent.generation.service.ResourceTemplateLoader()),
                versions,
                id -> Optional.of(GenerationErrorCode.EMPTY_REQUEST),   // 아무 코드나 — 막히는지만 본다
                jobs);

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
                versions, guard, store);

        var r = s2.start(blank(1L, "여름 데이터 이벤트"));

        assertInstanceOf(GenerationService.StartResult.AlreadyRunning.class, r);
        assertEquals(held.jobId(),
                ((GenerationService.StartResult.AlreadyRunning) r).job().jobId(),
                "돌고 있는 작업을 안 돌려줬습니다. 화면이 진행 상태를 이어 보여줄 수 없습니다.");
    }
}
