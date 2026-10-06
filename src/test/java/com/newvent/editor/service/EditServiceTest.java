package com.newvent.editor.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.editor.exception.EditErrorCode;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.service.EventGuard;
import com.newvent.generation.service.GenerationJob;
import com.newvent.generation.service.GenerationJobStore;
import com.newvent.generation.service.HtmlPolicy;
import com.newvent.generation.service.LlmCallRecorder;
import com.newvent.generation.service.RetryService;
import com.newvent.generation.service.VersionStore;
import com.newvent.infra.llm.LlmCallContext;
import com.newvent.infra.llm.LlmCallGateway;
import com.newvent.infra.llm.LlmProps;
import com.newvent.registry.Block;
import com.newvent.registry.BlockValidator;

/**
 * 수정 흐름.
 */
class EditServiceTest {

    // ── 가짜들 ────────────────────────────────────────────────────

    /** 라우터. 무엇을 돌려줄지 테스트가 정한다 */
    static final class FakeRouter extends RouteService {

        private Optional<List<RawRoute>> next = Optional.empty();
        private int calls;
        private String lastRequest;

        FakeRouter() {
            super(new LlmCallGateway.Direct(null), LlmCallRecorder.none());
        }

        void willReturn(RawRoute... ops) { this.next = Optional.of(List.of(ops)); }
        void willNotUnderstand()         { this.next = Optional.empty(); }
        int calls()                      { return calls; }

        @Override
        public Optional<List<RawRoute>> route(LlmCallContext ctx, String requestText) {
            calls++;
            lastRequest = requestText;
            return next;
        }
    }

    /** 재시도 루프. 결과를 줄 세워 놓고 하나씩 꺼낸다 */
    static final class FakeRetry extends RetryService {

        private final Deque<RetryService.Result> queue = new ArrayDeque<>();
        private final List<String> prompts = new ArrayList<>();
        private IntConsumer onCall = n -> { };

        FakeRetry() {
            // ★ Direct 라 reserve() 가 no-op 이다 — 상한 검사가 테스트에 끼어들지 않는다.
            //   maxRetry = 1 → maxAttempts() = 2. run() 을 덮었으니 값 자체는 쓰이지 않는다
            super(new LlmCallGateway.Direct(null),
                    new LlmProps(null, null, null, null, 0, 1, 0));
        }

        void willReturn(RetryService.Result... rs) {
            for (RetryService.Result r : rs) queue.add(r);
        }

        /** n 번째 호출 직후에 끼어든다. 중단 테스트가 쓴다 */
        void onCall(IntConsumer hook) { this.onCall = hook; }

        int calls()            { return prompts.size(); }
        List<String> prompts() { return prompts; }

        @Override
        public RetryService.Result run(LlmCallContext ctx, String system,
                                       String user, HtmlPolicy policy) {
            prompts.add(user);
            onCall.accept(prompts.size());
            RetryService.Result r = queue.poll();
            assertNotNull(r, prompts.size() + "번째 호출인데 줄 세워 둔 결과가 없습니다");
            return r;
        }
    }

    /** reserve 가 몇 번 얼마씩 불렸는지 센다. call 은 안 쓰인다 — 가짜가 덮었다 */
    static final class CountingGateway implements LlmCallGateway {
        final List<Integer> reserves = new ArrayList<>();

        @Override public void reserve(int expectedCalls) { reserves.add(expectedCalls); }

        @Override
        public com.newvent.infra.llm.LlmClient.Response call(
                LlmCallContext ctx, com.newvent.infra.llm.LlmClient.Request req) {
            throw new UnsupportedOperationException("가짜가 덮었어야 한다");
        }
    }

    private static RetryService.Result ok(String html) {
        return new RetryService.Result(true, html, List.of());
    }

    private static RetryService.Result validationFail() {
        return new RetryService.Result(false, null, List.of());
    }

    // ── 픽스처 ────────────────────────────────────────────────────

    private static final long EVENT = 1L;

    /**
     * 저장된 페이지. 섹션 네 개.
     * ★ hero 안에 data-slot 이 있다 — 병합이 그걸 살려 두는지 보려면 있어야 한다
     * ★ steps 가 있다 — canDelete() 가 참인 유일한 블록이다
     */
    private static final String BASE = """
            <section data-block="hero"><h1>여름 데이터 대방출</h1>\
            <p>기간 <span data-slot="period"></span> 까지</p></section>
            <section data-block="benefits"><ul><li>데이터 10GB</li><li>쿠폰</li></ul></section>
            <section data-block="steps"><ol><li>앱 열기</li><li>버튼 누르기</li></ol></section>
            <section data-block="cta"><button class="btn">참여하기</button></section>""";

    private static final String NEW_HERO =
            "<section data-block=\"hero\"><h1>가을 대축제</h1>"
            + "<p>기간 <span data-slot=\"period\"></span> 까지</p></section>";

    /** steps 가 없는 문서. 백지 생성이 선택 블록을 빼먹었거나 삭제한 뒤의 상태 */
    private static final String NO_STEPS = """
            <section data-block="hero"><h1>여름 데이터 대방출</h1>\
            <p>기간 <span data-slot="period"></span> 까지</p></section>
            <section data-block="benefits"><ul><li>데이터 10GB</li><li>쿠폰</li></ul></section>
            <section data-block="notices"><p>유의사항</p></section>
            <section data-block="cta"><button class="btn">참여하기</button></section>""";

    private static final String NEW_STEPS =
            "<section data-block=\"steps\"><ol><li>앱 열기</li><li>버튼 누르기</li></ol></section>";

    private static final String NEW_BENEFITS =
            "<section data-block=\"benefits\"><ul><li>데이터 20GB</li><li>쿠폰</li></ul></section>";

    private CountingGateway gateway;
    private FakeRouter router;
    private FakeRetry retry;
    private GenerationJobStore jobs;
    private VersionStore versions;
    private EditService service;

    @BeforeEach
    void setUp() {
        router = new FakeRouter();
        retry = new FakeRetry();
        jobs = new GenerationJobStore();
        versions = new VersionStore.InMemory();
        gateway = new CountingGateway();
        service = new EditService(router, retry, versions, new EventGuard.Open(), jobs,
                LlmCallRecorder.none(), gateway);

        // 고칠 페이지를 하나 심는다. 이게 v1 이고 기준 버전이 된다
        versions.save(EVENT, BASE, null);
    }

    // ── 도우미 ────────────────────────────────────────────────────

    private EditCommand cmd(String text) {
        return new EditCommand(EVENT, "여름 이벤트", text);
    }

    private GenerationJob started(EditService.StartResult r) {
        assertInstanceOf(EditService.StartResult.Started.class, r, "시작되지 않았습니다: " + r);
        return ((EditService.StartResult.Started) r).job();
    }

    private GenerationJob run(String text) {
        return await(started(service.start(cmd(text))));
    }

    /** 워커 스레드가 끝날 때까지. 모델을 안 부르므로 2초면 충분하다 */
    private static GenerationJob await(GenerationJob job) {
        long deadline = System.currentTimeMillis() + 2000;
        while (!job.done() && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(job.done(), "2초 안에 안 끝났습니다. 현재 단계=" + job.phase()
                + " — 워커가 막혔거나 예외가 새어 나갔습니다.");
        return job;
    }

    /** 몇 번째 버전까지 왔나. 처음 심은 게 1 이므로 저장이 없었으면 1 이다 */
    private int versionNo() {
        return versions.latest(EVENT).orElseThrow().versionNo();
    }

    private String savedHtml() {
        return versions.latest(EVENT).orElseThrow().html();
    }

    private static String blockOf(String doc, Block b) {
        return BlockValidator.blockOf(doc, b);
    }

    // ── 되묻기 ────────────────────────────────────────────────────

    @Test
    void 개인정보는_확인_전_호출과_저장이_없고_확인_후_원문으로_진행한다() {
        String request = "제목에 a@example.com 안내 문구 추가해줘";
        GenerationJob question = run(request);
        assertEquals(GenerationJob.Phase.ASK_BACK, question.phase());
        assertTrue(question.privacyConfirmationRequired());
        assertEquals(0, router.calls());
        assertEquals(0, retry.calls());
        assertTrue(gateway.reserves.isEmpty());
        assertEquals(1, versionNo());
        router.willReturn(new RawRoute("EDIT", "hero", null));
        retry.willReturn(ok(NEW_HERO));
        GenerationJob reply = await(started(service.start(new EditCommand(
                EVENT, "여름 이벤트", request, question.jobId(), true))));
        assertEquals(GenerationJob.Phase.DONE, reply.phase());
        assertTrue(router.lastRequest.contains("a@example.com"));
        assertTrue(retry.prompts().getFirst().contains("a@example.com"));
        assertEquals(2, versionNo());
    }

    @Test
    void 개인정보_수정후_재요청은_확인없이_일반_요청으로_진행한다() {
        run("전화번호 010-1234-5678 넣어줘");
        router.willReturn(new RawRoute("EDIT", "hero", null));
        retry.willReturn(ok(NEW_HERO));
        assertEquals(GenerationJob.Phase.DONE, run("제목을 가을 축제로 바꿔줘").phase());
        assertFalse(router.lastRequest.contains("010-1234"));
    }

    @Test
    void 개인정보_확인중_버전이_바뀌면_진행하지_않는다() {
        GenerationJob question = run("a@example.com 넣어줘");
        versions.save(EVENT, NEW_HERO, versions.latest(EVENT).orElseThrow().versionId());
        var result = service.start(new EditCommand(EVENT, "여름 이벤트",
                "a@example.com 넣어줘", question.jobId(), true));
        assertInstanceOf(EditService.StartResult.Rejected.class, result);
        assertEquals(0, router.calls());
    }

    @Test
    @DisplayName("라우터가 못 알아들으면 되묻고 모델을 더 부르지 않는다")
    void 라우터가_못_읽으면_되묻는다() {
        router.willNotUnderstand();

        GenerationJob job = run("어쩌구 저쩌구");

        assertEquals(GenerationJob.Phase.ASK_BACK, job.phase());
        assertNotNull(job.message(), "되묻기 질문이 있어야 한다");
        assertEquals(0, retry.calls(), "블록 수정 모델을 부르면 안 된다");
        assertEquals(1, versionNo(), "행이 생기면 안 된다");
    }

    @Test
    @DisplayName("되묻기는 done 이다 — 아니면 프론트가 영원히 폴링한다")
    void 되묻기도_끝난_작업이다() {
        router.willNotUnderstand();

        GenerationJob job = run("어쩌구");

        assertTrue(job.done());
        assertEquals(100, job.phase().percent());
    }

    @Test
    @DisplayName("내용 없는 항목 추가는 되묻는다 — 모델이 지어내면 안 된다")
    void 내용_없는_추가는_되묻는다() {
        router.willReturn(new RawRoute("ADD", "steps", null));

        GenerationJob job = run("참여 방법 하나 더 추가해줘");

        assertEquals(GenerationJob.Phase.ASK_BACK, job.phase());
        assertEquals(0, retry.calls());
        assertEquals(1, versionNo());
    }

    // ── 거절 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("서버 소유 영역은 거절하고 모델을 안 부른다")
    void 서버_소유_영역은_거절한다() {
        router.willReturn(new RawRoute("EDIT", "notices", "유의사항 바꿔"));

        GenerationJob job = run("유의사항 문구 바꿔줘");

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertNotNull(job.message());
        assertEquals(0, retry.calls(), "거절인데 모델을 불렀다 — 돈이 나간다");
        assertEquals(1, versionNo());
    }

    @Test
    @DisplayName("하나라도 거절이면 통과한 것도 실행하지 않는다")
    void 하나라도_거절이면_아무것도_안_한다() {
        router.willReturn(
                new RawRoute("EDIT", "hero", "가을 대축제"),     // 통과할 것
                new RawRoute("EDIT", "notices", null));          // 거절될 것

        GenerationJob job = run("제목 바꾸고 유의사항도 바꿔줘");

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertEquals(0, retry.calls(), "hero 를 먼저 고치면 안 된다 — 판정이 다 끝난 뒤에 시작한다");
        assertEquals(1, versionNo());
        assertTrue(savedHtml().contains("여름 데이터 대방출"), "원본이 그대로여야 한다");
    }

    @Test
    @DisplayName("같은 영역 요청이 두 개면 거절한다")
    void 같은_영역_요청이_두_개면_거절한다() {
        router.willReturn(
                new RawRoute("DELETE", "steps", null),
                new RawRoute("EDIT", "steps", "더 친절하게"));

        GenerationJob job = run("참여방법 지우고 참여방법 다듬어줘");

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertEquals(0, retry.calls());
        assertEquals(1, versionNo());
    }

    // ── 성공 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("성공하면 한 행이 저장되고 기준 버전을 가리킨다")
    void 성공하면_한_행이_저장된다() {
        router.willReturn(new RawRoute("EDIT", "hero", "가을 대축제"));
        retry.willReturn(ok(NEW_HERO));

        GenerationJob job = run("제목을 가을 대축제로 바꿔줘");

        assertEquals(GenerationJob.Phase.DONE, job.phase());
        assertEquals(2, versionNo(), "저장은 한 번뿐이다");
        assertNotNull(job.versionId(), "완료된 작업은 버전 id 를 들고 있어야 한다");
        assertEquals(versions.latest(EVENT).orElseThrow().versionId(), job.versionId());
        assertTrue(savedHtml().contains("가을 대축제"));
    }

    @Test
    @DisplayName("고친 영역만 바뀌고 나머지와 data-slot 은 그대로다")
    void 다른_영역과_슬롯은_건드리지_않는다() {
        router.willReturn(new RawRoute("EDIT", "hero", "가을 대축제"));
        retry.willReturn(ok(NEW_HERO));

        run("제목 바꿔줘");
        String saved = savedHtml();

        assertTrue(saved.contains("data-slot=\"period\""), "슬롯이 사라지면 기간을 영영 못 채운다");
        assertEquals(blockOf(BASE, Block.BENEFITS), blockOf(saved, Block.BENEFITS));
        assertEquals(blockOf(BASE, Block.STEPS), blockOf(saved, Block.STEPS));
        assertEquals(blockOf(BASE, Block.CTA), blockOf(saved, Block.CTA));
    }

    @Test
    @DisplayName("연산 두 개가 버전 하나에 같이 들어간다")
    void 연산_두_개가_한_버전에_들어간다() {
        router.willReturn(
                new RawRoute("EDIT", "hero", "가을 대축제"),
                new RawRoute("EDIT", "benefits", null));
        retry.willReturn(ok(NEW_HERO), ok(NEW_BENEFITS));

        GenerationJob job = run("제목 바꾸고 혜택도 다듬어줘");

        assertEquals(GenerationJob.Phase.DONE, job.phase());
        assertEquals(2, retry.calls(), "연산마다 한 번씩 부른다");
        assertEquals(2, versionNo(), "두 연산이 두 행이 되면 안 된다");

        String saved = savedHtml();
        assertTrue(saved.contains("가을 대축제"));
        assertTrue(saved.contains("데이터 20GB"));
    }

    @Test
    @DisplayName("두 번째 연산은 첫 번째 결과 위에 얹는다")
    void 앞_연산의_결과_위에_얹는다() {
        router.willReturn(
                new RawRoute("EDIT", "hero", "가을 대축제"),
                new RawRoute("EDIT", "benefits", null));
        retry.willReturn(ok(NEW_HERO), ok(NEW_BENEFITS));

        run("제목 바꾸고 혜택도 다듬어줘");

        // ★ 두 번째 호출의 프롬프트에 첫 번째 결과가 들어 있어야 한다.
        //   원본을 매번 새로 읽으면 여기서 "여름 데이터 대방출" 이 보인다
        String second = retry.prompts().get(1);
        assertTrue(second.contains("데이터 10GB"), "현재 benefits 내용을 줘야 한다");
    }

    // ── 실패 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("검증 실패는 실패로 끝난다 — 원본을 되돌려 저장하지 않는다")
    void 검증_실패면_저장하지_않는다() {
        router.willReturn(new RawRoute("EDIT", "hero", "가을 대축제"));
        retry.willReturn(validationFail());

        GenerationJob job = run("제목 바꿔줘");

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertNotNull(job.message());
        assertEquals(1, versionNo(), "실패인데 행이 생겼다");
        assertNull(job.versionId());
    }

    @Test
    @DisplayName("연산 두 개 중 두 번째가 실패하면 첫 번째도 저장하지 않는다")
    void 뒤에서_실패하면_앞도_버린다() {
        router.willReturn(
                new RawRoute("EDIT", "hero", "가을 대축제"),
                new RawRoute("EDIT", "benefits", null));
        retry.willReturn(ok(NEW_HERO), validationFail());

        GenerationJob job = run("제목 바꾸고 혜택도 다듬어줘");

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertEquals(1, versionNo());
        assertTrue(savedHtml().contains("여름 데이터 대방출"), "원본이 그대로여야 한다");
    }

    // ── 삭제 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("삭제는 모델을 부르지 않고 섹션만 걷어낸다")
    void 삭제는_모델을_안_부른다() {
        router.willReturn(new RawRoute("DELETE", "steps", null));

        GenerationJob job = run("참여방법 영역 지워줘");

        assertEquals(GenerationJob.Phase.DONE, job.phase());
        assertEquals(0, retry.calls(), "지우는 데 모델이 필요 없다");
        assertEquals(2, versionNo());

        String saved = savedHtml();
        assertEquals("", blockOf(saved, Block.STEPS), "steps 가 남아 있다");
        assertEquals(blockOf(BASE, Block.HERO), blockOf(saved, Block.HERO),
                "지우면서 옆 섹션이 바뀌었다");
        assertTrue(saved.contains("data-slot=\"period\""));
    }

    @Test
    @DisplayName("필수 영역 삭제는 거절한다")
    void 필수_영역은_못_지운다() {
        router.willReturn(new RawRoute("DELETE", "hero", null));

        GenerationJob job = run("제목 영역 지워줘");

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertEquals(1, versionNo());
    }

    // ── 중단 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("중단이 연산 사이에서 걸리고 아무것도 저장하지 않는다")
    void 중단은_연산_사이에서_걸린다() {
        router.willReturn(new RawRoute("EDIT", "hero", "가을 대축제"));
        retry.willReturn(ok(NEW_HERO));

        // ★ 첫 호출이 끝난 직후에 중단을 요청한다.
        //   jobs 에는 worker.submit 전에 이미 들어 있으므로 경합이 없다
        retry.onCall(n -> jobs.ofEvent(EVENT).ifPresent(GenerationJob::requestCancel));

        GenerationJob job = run("제목 바꿔줘");

        assertEquals(GenerationJob.Phase.CANCELLED, job.phase());
        assertEquals(1, versionNo(), "중단인데 행이 생겼다");
        assertNull(job.versionId());
    }

    // ── 진입 검사 ─────────────────────────────────────────────────

    @Test
    @DisplayName("고칠 페이지가 없으면 시작 자체를 거부한다")
    void 페이지가_없으면_시작을_거부한다() {
        EditService.StartResult r = service.start(new EditCommand(99L, "빈 이벤트", "제목 바꿔줘"));

        assertInstanceOf(EditService.StartResult.Rejected.class, r);
        assertEquals(EditErrorCode.PAGE_NOT_FOUND,
                ((EditService.StartResult.Rejected) r).errorCode());
        assertEquals(0, router.calls(), "라우터도 부르면 안 된다");
    }

    @Test
    @DisplayName("빈 요청은 시작 자체를 거부한다")
    void 빈_요청은_시작을_거부한다() {
        EditService.StartResult r = service.start(cmd("   "));

        assertInstanceOf(EditService.StartResult.Rejected.class, r);
        assertEquals(GenerationErrorCode.EMPTY_REQUEST,
                ((EditService.StartResult.Rejected) r).errorCode());
        assertEquals(0, router.calls());
    }

    @Test
    @DisplayName("이미 진행 중이면 자리를 잡지 않고 거부한다")
    void 이미_진행_중이면_거부한다() {
        jobs.start(EVENT);   // 생성이 돌고 있는 상황

        EditService.StartResult r = service.start(cmd("제목 바꿔줘"));

        assertInstanceOf(EditService.StartResult.AlreadyRunning.class, r);
        assertEquals(0, router.calls());
    }

    // ── 프롬프트 ──────────────────────────────────────────────────

    @Test
    @DisplayName("요청문 전체와 현재 내용을 모델에게 준다")
    void 프롬프트에_요청문과_현재_내용이_들어간다() {
        router.willReturn(new RawRoute("EDIT", "hero", null));
        retry.willReturn(ok(NEW_HERO));

        run("제목을 더 짧고 힘있게 다듬어줘");

        String prompt = retry.prompts().get(0);
        assertTrue(prompt.contains("제목을 더 짧고 힘있게 다듬어줘"),
                "content 가 null 이면 요청문이 유일한 지시다");
        assertTrue(prompt.contains("여름 데이터 대방출"), "현재 내용이 없으면 새로 써 온다");
        assertTrue(prompt.contains("여름 이벤트"), "이벤트 제목도 준다");
    }

    @Test
    @DisplayName("관리자가 직접 쓴 문구는 따로 표시해 준다")
    void 직접_쓴_문구를_강조한다() {
        router.willReturn(new RawRoute("EDIT", "hero", "가을 대축제"));
        retry.willReturn(ok(NEW_HERO));

        run("제목을 가을 대축제로 바꿔줘");

        assertTrue(retry.prompts().get(0).contains("직접 쓴 문구"));
    }

    // ── 상한 확보 ─────────────────────────────────────────────────

    @Test
    @DisplayName("삭제만 있으면 모델 몫의 상한을 잡지 않는다")
    void 삭제는_상한을_잡지_않는다() {
        router.willReturn(new RawRoute("DELETE", "steps", null));

        run("참여방법 영역 지워줘");

        // start() 가 라우터 1 + 연산 하나(2) = 3 을 잡는다. 그 뒤로는 없어야 한다
        assertEquals(List.of(1 + retry.maxAttempts()), gateway.reserves,
                "DELETE 는 모델을 안 부르는데 상한을 잡았다");
    }

    @Test
    @DisplayName("삭제와 수정이 섞이면 수정 몫만 잡는다")
    void 섞이면_수정_몫만_잡는다() {
        router.willReturn(
                new RawRoute("DELETE", "steps", null),
                new RawRoute("EDIT", "hero", "가을 대축제"));
        retry.willReturn(ok(NEW_HERO));

        run("참여방법 지우고 제목도 바꿔줘");

        assertEquals(List.of(1 + retry.maxAttempts(), retry.maxAttempts()), gateway.reserves,
                "연산 2개지만 모델을 부르는 건 하나다");
    }

    // ── 문서에 없는 영역 ──────────────────────────────────────────

    @Test
    @DisplayName("없는 영역 수정은 모델을 부르기 전에 거절한다")
    void 없는_영역_수정은_모델_전에_거절한다() {
        setUpWith(NO_STEPS);
        router.willReturn(new RawRoute("EDIT", "steps", "더 친절하게"));

        GenerationJob job = run("참여방법을 더 친절하게 다듬어줘");

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertEquals(0, retry.calls(), "없는 영역인데 모델을 불렀다 — 호출 예산이 샌다");
        assertEquals(1, versionNo());
        assertEquals(List.of(1 + retry.maxAttempts()), gateway.reserves,
                "판정에서 끝났으니 두 번째 확보가 없어야 한다");
    }

    @Test
    @DisplayName("없던 영역 추가는 문서 순서에 맞는 자리에 삽입된다")
    void 없던_영역은_삽입된다() {
        setUpWith(NO_STEPS);
        router.willReturn(new RawRoute("ADD", "steps", "앱 열고 버튼 누르기"));
        retry.willReturn(ok(NEW_STEPS));

        GenerationJob job = run("참여 방법 넣어줘");

        assertEquals(GenerationJob.Phase.DONE, job.phase());
        assertEquals(2, versionNo());

        String saved = savedHtml();
        assertFalse(blockOf(saved, Block.STEPS).isBlank(), "steps 가 안 들어갔다");
        // benefits < steps < notices 순서여야 한다
        assertTrue(saved.indexOf("data-block=\"benefits\"") < saved.indexOf("data-block=\"steps\""));
        assertTrue(saved.indexOf("data-block=\"steps\"") < saved.indexOf("data-block=\"notices\""));
    }

    @Test
    @DisplayName("이미 있는 영역에 대한 추가는 수정으로 본다")
    void 있는_영역_추가는_수정이다() {
        router.willReturn(new RawRoute("ADD", "steps", "앱 열고 버튼 누르기"));
        retry.willReturn(ok(NEW_STEPS));

        GenerationJob job = run("참여 방법 추가해줘");

        assertEquals(GenerationJob.Phase.DONE, job.phase());
        assertEquals(2, versionNo());
        // 삽입이 아니라 병합이므로 steps 는 하나뿐이다
        assertEquals(1, savedHtml().split("data-block=\"steps\"", -1).length - 1);
    }

    @Test
    @DisplayName("혜택 항목 추가는 모델을 부르기 전에 거절한다")
    void 혜택_항목_추가는_거절한다() {
        router.willReturn(new RawRoute("ADD", "benefits", "데이터 20GB 증정"));

        GenerationJob job = run("혜택에 데이터 20GB 증정을 추가해줘");

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertTrue(job.message().contains("늘리거나 줄일 수 없습니다"));
        assertEquals(0, retry.calls(), "거절인데 모델을 불렀다");
        assertEquals(1, versionNo());
    }

    // ── 고른 영역 (미리보기에서 클릭한 블록) ───────────────────────

    private GenerationJob runOn(String text, String... blocks) {
        return await(started(service.start(new EditCommand(EVENT, "여름 이벤트", text, List.of(blocks)))));
    }

    @Test
    @DisplayName("고른 영역만 고친다 — 라우터가 다른 영역을 짚어도 버린다")
    void 고른_영역만_고친다() {
        router.willReturn(new RawRoute("EDIT", "hero", null));
        retry.willReturn(ok(NEW_BENEFITS));

        GenerationJob job = runOn("좀 더 눈에 띄게 바꿔줘", "benefits");

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertEquals(1, retry.calls(), "고른 영역 하나만 모델을 불러야 한다");
        assertTrue(retry.prompts().get(0).contains("data-block=\"benefits\""), "benefits 를 고쳐야 한다");
        assertTrue(savedHtml().contains("데이터 20GB"));
        assertEquals(blockOf(BASE, Block.HERO), blockOf(savedHtml(), Block.HERO), "고르지 않은 hero 가 바뀌었다");
    }

    @Test
    @DisplayName("영역을 골랐으면 라우터가 못 읽어도 되묻지 않고 그 영역을 고친다")
    void 고른_영역이면_되묻지_않는다() {
        router.willNotUnderstand();
        retry.willReturn(ok(NEW_BENEFITS));

        GenerationJob job = runOn("이거 더 예쁘게", "benefits");

        assertEquals(GenerationJob.Phase.DONE, job.phase(), "되묻지 말고 고른 영역을 고쳐야 한다: " + job.message());
        assertEquals(2, versionNo());
    }

    @Test
    @DisplayName("요청문의 동작은 살린다 — 고른 영역을 지워 달라면 지운다 (모델 없음)")
    void 고른_영역의_삭제는_살린다() {
        router.willReturn(new RawRoute("DELETE", "steps", null));

        GenerationJob job = runOn("이거 지워줘", "steps");

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertEquals(0, retry.calls(), "삭제는 모델을 부르지 않는다");
        assertTrue(blockOf(savedHtml(), Block.STEPS).isBlank(), "steps 가 남았다");
    }

    @Test
    @DisplayName("고른 영역 중 라우터가 못 짚은 것은 EDIT 으로 채워 둘 다 고친다 — 선언 순서로 돈다")
    void 못_짚은_영역은_EDIT_으로_채운다() {
        router.willReturn(new RawRoute("EDIT", "hero", "가을 대축제"));
        retry.willReturn(ok(NEW_HERO),
                ok("<section data-block=\"cta\"><button class=\"btn\">지금 참여</button></section>"));

        GenerationJob job = runOn("분위기 바꿔줘", "cta", "hero");

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertEquals(2, retry.calls());
        assertTrue(retry.prompts().get(0).contains("data-block=\"hero\""), "hero 가 먼저다 (Block 선언 순서)");
        assertTrue(savedHtml().contains("가을 대축제") && savedHtml().contains("지금 참여"));
    }

    @Test
    @DisplayName("같은 고른 영역에 연산이 여러 개여도 거절하지 않고 첫 연산만 쓴다")
    void 같은_영역_연산이_여럿이어도_하나만() {
        router.willReturn(new RawRoute("EDIT", "steps", "앱 열기"), new RawRoute("EDIT", "steps", "버튼 누르기"));
        retry.willReturn(ok(NEW_STEPS));

        GenerationJob job = runOn("단계를 다듬어줘. 앱 열기, 버튼 누르기", "steps");

        assertEquals(GenerationJob.Phase.DONE, job.phase(), "같은 부분 요청 두 개로 거절되면 안 된다: " + job.message());
        assertEquals(1, retry.calls());
    }

    @Test
    @DisplayName("유의사항 · 없는 영역을 고르면 400 이고 라우터도 부르지 않는다")
    void 못_고르는_영역은_거절() {
        for (String bad : List.of("notices", "nope")) {
            EditService.StartResult r = service.start(new EditCommand(EVENT, "여름 이벤트", "바꿔줘", List.of(bad)));

            assertInstanceOf(EditService.StartResult.Rejected.class, r, bad);
            assertEquals(EditErrorCode.INVALID_BLOCK, ((EditService.StartResult.Rejected) r).errorCode(), bad);
        }
        assertEquals(0, router.calls(), "거절인데 라우터를 불렀다");
    }

    @Test
    @DisplayName("고른 영역의 중복 · 빈 값은 접힌다")
    void 고른_영역_정리() {
        EditCommand c = new EditCommand(EVENT, "t", "x", java.util.Arrays.asList("hero", " hero ", "", null, "cta"));
        assertEquals(List.of("hero", "cta"), c.blocks());
        assertTrue(new EditCommand(EVENT, "t", "x").blocks().isEmpty());
    }

    /** 기준 문서를 바꿔 다시 세운다 */
    private void setUpWith(String html) {
        versions = new VersionStore.InMemory();
        gateway = new CountingGateway();
        service = new EditService(router, retry, versions, new EventGuard.Open(), jobs,
                LlmCallRecorder.none(), gateway);
        versions.save(EVENT, html, null);
    }
}
