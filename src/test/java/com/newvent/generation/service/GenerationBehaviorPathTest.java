package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.infra.llm.LlmCallContext;
import com.newvent.infra.llm.LlmCallGateway;
import com.newvent.infra.llm.LlmClient;
import com.newvent.registry.BlockValidator;

/**
 * 백지 생성의 동작 배치 — HTML 생성 → 동작 배치기(LLM, JSON) → 서버가 검증해서 심기 → 저장.
 *
 * ★ HTML 생성은 GenerationServiceTest 의 가짜 재시도가, 배치기는 아래 가짜 게이트웨이가 답한다
 */
class GenerationBehaviorPathTest {

    private static final String GENERATED = """
            <section data-block="hero"><h1>여름 데이터 대방출</h1><p>걱정 없이</p></section>
            <section data-block="benefits"><ul><li>데이터 10GB</li><li>쿠폰</li></ul></section>
            <section data-block="cta"><a href="#" class="btn">참여하기</a></section>
            """;

    /** 배치기 호출만 받는 게이트웨이 — 답을 정해 두고, 받은 요청을 모은다 */
    static final class FakeGateway implements LlmCallGateway {
        final List<LlmClient.Request> requests = new ArrayList<>();
        final List<LlmCallContext> contexts = new ArrayList<>();
        Function<LlmClient.Request, LlmClient.Response> answer;

        @Override public void reserve(int expectedCalls) { }

        @Override
        public LlmClient.Response call(LlmCallContext ctx, LlmClient.Request req) {
            requests.add(req);
            contexts.add(ctx);
            return answer.apply(req);
        }
    }

    private static LlmClient.Response json(String content) {
        return new LlmClient.Response(content, 10, 10, 1, false);
    }

    private final GenerationServiceTest.FakeRetry retry = new GenerationServiceTest.FakeRetry();
    private final VersionStore versions = new VersionStore.InMemory();
    private final FakeGateway gateway = new FakeGateway();
    private final GenerationService service = new GenerationService(
            retry, new TemplateService(new ResourceTemplateLoader()), versions, new EventGuard.Open(),
            new GenerationJobStore(), LlmCallRecorder.none(), new com.newvent.filtering.FilteringPolicy(),
            new GenerationServiceTest.FakeSimilarity(), gateway);

    private Document generate(String request) {
        retry.willReturn(new RetryService.Result(true, BlockValidator.sanitizeGenerated(GENERATED), List.of()));
        GenerationService.StartResult r = service.start(new GenerateCommand(
                1L, null, false, "여름 이벤트", "2026.07.01 ~ 07.31", null, request));
        GenerationJob job = ((GenerationService.StartResult.Started) r).job();
        long deadline = System.currentTimeMillis() + 2000;
        while (!job.done() && System.currentTimeMillis() < deadline) Thread.onSpinWait();
        assertEquals(GenerationJob.Phase.DONE, job.phase(), job.message());
        return Jsoup.parseBodyFragment(versions.latest(1L).orElseThrow().html());
    }

    @Test
    @DisplayName("★ 배치기가 고른 대로 심는다 — 모델은 JSON 만, 서버가 이동 버튼 · 참여 버튼을 만든다 (타이머는 안 골랐다)")
    void 배치기가_고른_대로() {
        gateway.answer = req -> json("{\"actions\":["
                + "{\"behavior\":\"scroll-to\",\"block\":\"hero\",\"target\":\"benefits\"},"
                + "{\"behavior\":\"participate\",\"block\":\"cta\",\"target\":null}]}");

        Document d = generate("신규 가입 혜택 안내 페이지 만들어줘");

        assertEquals(1, gateway.requests.size(), "배치기는 한 번만 부른다");
        assertEquals("ev-benefits", d.selectFirst("[data-block=hero] [data-behavior=scroll-to]").attr("data-target"));
        assertEquals("participate", d.selectFirst("[data-block=cta] a.btn").attr("data-behavior"));
        assertNull(d.selectFirst("[data-behavior=countdown]"), "고르지 않은 타이머를 심었습니다");
    }

    @Test
    @DisplayName("배치기에는 페이지 영역과 요청문이 가고, HTML 생성과 다른 묶음으로 기록된다")
    void 배치기_입력() {
        gateway.answer = req -> json("{\"actions\":[]}");

        generate("이번 주말까지만 데이터 2배");

        LlmClient.Request req = gateway.requests.get(0);
        assertTrue(req.system().contains("동작 배치기"));
        assertTrue(req.user().contains("hero") && req.user().contains("benefits") && req.user().contains("cta"), req.user());
        assertTrue(req.user().contains("이번 주말까지만 데이터 2배"), req.user());
        assertNotNull(gateway.contexts.get(0).requestId(), "배치기 호출도 로그에 남는다");
    }

    @Test
    @DisplayName("배치기가 아무것도 안 골라도 참여 버튼은 붙는다")
    void 빈_계획에도_참여_버튼() {
        gateway.answer = req -> json("{\"actions\":[]}");

        Document d = generate("가을 이벤트");

        assertEquals("participate", d.selectFirst("[data-block=cta] a.btn").attr("data-behavior"));
        assertNull(d.selectFirst("[data-behavior=countdown]"));
    }

    @Test
    @DisplayName("★ 배치기가 실패해도 생성은 성공한다 — 규격 밖 출력 · 잘림 · 호출 예외 모두 기본 배치로")
    void 실패하면_기본_배치() {
        for (Function<LlmClient.Request, LlmClient.Response> broken : List.<Function<LlmClient.Request, LlmClient.Response>>of(
                req -> json("모르겠어요"),
                req -> new LlmClient.Response("{\"actions\":[", 10, 10, 1, true),
                req -> { throw new IllegalStateException("연결 실패"); })) {
            gateway.answer = broken;

            Document d = generate("가을 이벤트");

            assertNotNull(d.selectFirst("[data-block=hero] [data-behavior=countdown]"), "기본 배치의 타이머가 없습니다");
            assertNotNull(d.selectFirst("[data-block=hero] [data-behavior=scroll-to]"), "기본 배치의 이동 버튼이 없습니다");
            assertEquals("participate", d.selectFirst("[data-block=cta] a.btn").attr("data-behavior"));
        }
    }
}
