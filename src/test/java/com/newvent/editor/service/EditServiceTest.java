package com.newvent.editor.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

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
            //   maxRetry = 1 → maxAttempts() = 2. runEdit() 을 덮었으니 값 자체는 쓰이지 않는다
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
        public RetryService.Result runEdit(LlmCallContext ctx, String system,
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
        return ok(html, "영역 내용 수정");
    }

    private static RetryService.Result ok(String html, String changeSummary) {
        return new RetryService.Result(true, html, List.of(), changeSummary);
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

    /**
     * ★ BASE 의 steps 와 <b>달라야 한다.</b> 예전에는 글자 하나까지 같았고,
     *   그래서 "바뀐 게 없으면 저장하지 않는다" 가 들어오자 이 테스트가 깨졌다.
     *   이 테스트가 보려는 것은 "ADD 가 삽입이 아니라 병합인가" 이고,
     *   내용이 같았던 건 우연이다. 달라야 병합이 실제로 갈아끼웠는지까지 보인다.
     */
    private static final String NEW_STEPS =
            "<section data-block=\"steps\"><ol><li>앱 열고 로그인</li><li>버튼 누르기</li></ol></section>";

    /** BASE 의 steps 와 <b>똑같은</b> 응답. 모델이 아무것도 안 바꾼 상황을 만든다 */
    private static final String SAME_STEPS =
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
        versions = spy(new VersionStore.InMemory());
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

    // ── 조용한 실패 ───────────────────────────────────────────────

    /**
     * ★ 실측(여름 수영장): "참여 버튼을 파란색으로 물고기 느낌나게" 가 v4 로 저장되고
     *   "반영했어요" 가 나갔는데 화면은 한 글자도 안 바뀌었다. cta 변형 11개가 전부 모양이고
     *   색은 hero 에만 걸리는 팔레트라, 애초에 할 수 없는 요청이었다.
     *   관리자는 자기가 잘못 말한 줄 알고 같은 요청을 계속 다시 쓴다.
     */
    @Test
    @DisplayName("★ 모델이 똑같은 내용을 돌려주면 저장하지 않는다 — 틀린 성공보다 정직한 실패")
    void 안_바뀌면_저장하지_않는다() {
        router.willReturn(new RawRoute("STYLE", "steps", null));
        retry.willReturn(ok(SAME_STEPS));

        GenerationJob job = run("참여 방법을 물고기 느낌으로 바꿔줘");

        assertEquals(GenerationJob.Phase.FAILED, job.phase());
        assertEquals(1, versionNo(), "안 바뀌었는데 버전이 생겼다");
        assertNull(job.versionId());
    }

    /**
     * ★ "못 했습니다" 만 말하면 관리자가 같은 요청을 또 쓴다.
     *   그 영역에 실제로 있는 모양을 사람 말로 보여줘야 다음 요청이 맞는다.
     */
    @Test
    @DisplayName("무엇을 할 수 있는지 같이 알려준다")
    void 할_수_있는_것을_알려준다() {
        router.willReturn(new RawRoute("STYLE", "steps", null));
        retry.willReturn(ok(SAME_STEPS));

        GenerationJob job = run("참여 방법을 물고기 느낌으로 바꿔줘");

        assertTrue(job.message().contains("그대로 두었습니다"), job.message());
        assertTrue(job.message().contains("세로 타임라인"),
                "고를 수 있는 모양 이름이 없으면 관리자가 또 같은 요청을 쓴다: " + job.message());
        assertTrue(job.message().contains("전체 색감"),
                "색은 영역별로 못 바꾼다는 것을 알려줘야 한다: " + job.message());
    }

    /**
     * ★ 두 가지를 시켰는데 하나만 됐으면 된 쪽은 저장하는 게 맞다.
     *   전부 막으면 "제목은 바뀌었는데 안 저장됨" 이 되어 더 나쁘다.
     */
    @Test
    @DisplayName("연산 두 개 중 하나만 바뀌어도 저장한다")
    void 일부만_바뀌면_저장한다() {
        router.willReturn(
                new RawRoute("EDIT", "hero", "가을 대축제"),
                new RawRoute("EDIT", "steps", null));
        retry.willReturn(ok(NEW_HERO), ok(SAME_STEPS));

        GenerationJob job = run("제목 바꾸고 참여방법도 다듬어줘");

        assertEquals(GenerationJob.Phase.DONE, job.phase());
        assertEquals(2, versionNo());
        assertTrue(savedHtml().contains("가을 대축제"));
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

    // ── 선택 영역 수정은 페이지 전체 팔레트를 바꾸지 않는다 (리뷰 반영) ─────────

    private static final String WRAPPED = "<div class=\"ev-container event-page\">" + BASE + "</div>";

    /**
     * 팔레트 <b>와 내용이 같이</b> 바뀐 응답.
     *
     * ★ 예전에는 BASE 의 hero 와 글자가 같고 class 만 달랐다. 그런데 선택 영역 수정은
     *   팔레트를 떼어 내므로(withoutPalette), 떼고 나면 전후가 <b>완전히 동일</b>해진다.
     *   "바뀐 게 없으면 저장하지 않는다" 가 그걸 조용한 실패로 보고 막는 게 맞다.
     *   이 테스트가 보려는 것은 "팔레트가 페이지 전체로 새지 않는다" 이므로
     *   내용은 달라도 된다. 오히려 달라야 <b>"내용은 되고 색만 막혔다"</b> 까지 보인다.
     */
    private static final String HERO_WITH_PALETTE =
            "<section data-block=\"hero\" class=\"palette-summer\"><h1>시원한 여름 대방출</h1>"
            + "<p>기간 <span data-slot=\"period\"></span> 까지</p></section>";

    /** 팔레트 <b>만</b> 붙고 내용은 그대로. 떼고 나면 아무것도 안 바뀐 상태가 된다 */
    private static final String HERO_PALETTE_ONLY =
            "<section data-block=\"hero\" class=\"palette-summer\"><h1>여름 데이터 대방출</h1>"
            + "<p>기간 <span data-slot=\"period\"></span> 까지</p></section>";

    private static org.jsoup.nodes.Element root(String html) {
        return org.jsoup.Jsoup.parseBodyFragment(html).selectFirst(".ev-container");
    }

    @Test
    @DisplayName("hero 를 골라 고칠 때 — 프롬프트에 팔레트가 없고, 모델이 붙여도 떼어 내 페이지 루트 색이 그대로다")
    void 선택_영역_수정은_팔레트를_바꾸지_않는다() {
        setUpWith(WRAPPED);
        router.willReturn(new RawRoute("STYLE", "hero", null));
        retry.willReturn(ok(HERO_WITH_PALETTE));

        GenerationJob job = runOn("파란색으로 바꿔줘", "hero");

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertFalse(retry.prompts().isEmpty());
        assertFalse(savedHtml().contains("palette-summer"), "선택 영역 수정이 페이지 전체 팔레트를 바꿨습니다: " + savedHtml());
        assertFalse(root(savedHtml()).classNames().stream().anyMatch(c -> c.startsWith("palette-")));
    }

    @Test
    @DisplayName("영역을 고르지 않으면 지금처럼 hero 의 팔레트가 페이지 루트로 옮겨진다")
    void 영역을_안_고르면_팔레트_적용() {
        setUpWith(WRAPPED);
        router.willReturn(new RawRoute("STYLE", "hero", null));
        retry.willReturn(ok(HERO_WITH_PALETTE));

        GenerationJob job = run("전체 색감을 여름 느낌으로 바꿔줘");

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        assertTrue(root(savedHtml()).hasClass("palette-summer"), "팔레트가 루트로 옮겨져야 합니다: " + savedHtml());
    }

    /**
     * ★ 영역을 고른 채 색을 바꿔 달라는 요청은 <b>구조적으로 안 된다.</b>
     *   색은 페이지 전체 값(팔레트)이라, 고른 영역만 바꿀 방법이 없어서 떼어 낸다.
     *   그 결과 화면이 그대로인데, 관리자는 왜 안 됐는지 알 길이 없다.
     *
     *   예전 안내 문구는 hero 가 아닐 때만 색 얘기를 했다 —
     *   정작 hero 를 골라 색을 요청한 경우에 그 설명이 빠졌다.
     *
     * ★ "안 된다" 만으로는 모자란다. <b>무엇은 되는지</b>를 같이 줘야 다음 요청이 맞는다.
     */
    @Test
    @DisplayName("★ 영역을 고른 채 색을 바꿔 달라면 — 왜 안 되는지와 무엇이 되는지 알려준다")
    void 고른_영역의_색_요청은_이유를_알려준다() {
        setUpWith(WRAPPED);
        router.willReturn(new RawRoute("STYLE", "hero", null));
        retry.willReturn(ok(HERO_PALETTE_ONLY));

        GenerationJob job = runOn("파란색으로 바꿔줘", "hero");

        assertEquals(GenerationJob.Phase.FAILED, job.phase(), "안 바뀌었는데 저장했다");
        assertEquals(1, versionNo());
        assertTrue(job.message().contains("색은 페이지 전체"),
                "hero 를 골랐을 때도 색 얘기를 해줘야 한다: " + job.message());
        assertTrue(job.message().contains("영역 선택을 해제"),
                "어떻게 하면 되는지 알려줘야 한다: " + job.message());
        assertTrue(job.message().contains("밝기나 분위기"),
                "이 영역에서 되는 것도 알려줘야 한다: " + job.message());
    }

    /** 기준 문서를 바꿔 다시 세운다 */
    private void setUpWith(String html) {
        versions = new VersionStore.InMemory();
        gateway = new CountingGateway();
        service = new EditService(router, retry, versions, new EventGuard.Open(), jobs,
                LlmCallRecorder.none(), gateway);
        versions.save(EVENT, html, null);
    }

    @Test
    void LLM의_변경요약을_저장_메서드에_전달한다() {
        Long sourceVersionId = versions.latest(EVENT).orElseThrow().versionId();
        String summary = "제목을 가을 대축제로 변경";

        router.willReturn(new RawRoute("EDIT", "hero", null));
        retry.willReturn(ok(NEW_HERO, summary));

        GenerationJob job = run("제목을 가을 대축제로 바꿔줘");

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());

        verify(versions).save(
            eq(EVENT),
            anyString(),
            eq(sourceVersionId),
            eq(summary));
    }

    @Test
    void 여러_영역의_변경요약을_합쳐서_전달한다() {
        Long sourceVersionId = versions.latest(EVENT).orElseThrow().versionId();

        router.willReturn(
            new RawRoute("EDIT", "hero", null),
            new RawRoute("EDIT", "steps", null));
        retry.willReturn(
            ok(NEW_HERO, "제목을 가을 대축제로 변경"),
            ok(NEW_STEPS, "참여 방법에 로그인 안내 반영"));

        GenerationJob job = run("제목과 참여 방법을 바꿔줘");

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());

        verify(versions).save(
            eq(EVENT),
            anyString(),
            eq(sourceVersionId),
            eq("제목을 가을 대축제로 변경; 참여 방법에 로그인 안내 반영"));
    }

    @Test
    void 실제로_바뀌지_않은_영역의_요약은_제외한다() {
        Long sourceVersionId = versions.latest(EVENT).orElseThrow().versionId();

        router.willReturn(
            new RawRoute("EDIT", "hero", null),
            new RawRoute("EDIT", "steps", null));
        retry.willReturn(
            ok(NEW_HERO, "제목을 가을 대축제로 변경"),
            ok(SAME_STEPS, "참여 방법 문구 변경"));

        GenerationJob job = run("제목과 참여 방법을 다듬어줘");

        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());

        verify(versions).save(
            eq(EVENT),
            anyString(),
            eq(sourceVersionId),
            eq("제목을 가을 대축제로 변경"));
    }
}
