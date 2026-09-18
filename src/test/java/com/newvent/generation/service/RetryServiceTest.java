package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.infra.llm.LlmCallException;
import com.newvent.infra.llm.LlmClient;
import com.newvent.infra.llm.MockLlmClient;
import com.newvent.registry.BlockValidator;
import com.newvent.registry.PromptBuilder;

/**
 * 재시도 루프가 제대로 도는지 본다. 스프링 컨텍스트 안 띄운다.
 *
 * ★ 정상/비정상 HTML 을 여기에 직접 적지 않는다.
 *   MockLlmClient 가 이미 둘 다 만들 줄 안다. 블록 모양이 바뀌면 그쪽이 따라가고,
 *   여기에 적어두면 레지스트리를 고칠 때마다 이 파일도 같이 고쳐야 한다.
 */
class RetryServiceTest {

    // ── 도구 ──────────────────────────────────────────────

    /** 대본대로 답하는 가짜 모델. 대본이 끝나면 마지막 답을 계속 반복한다. */
    static class ScriptedLlm implements LlmClient {
        private final List<String> script;
        private int i = 0;
        /** 모델이 실제로 받은 user 프롬프트들 */
        final List<String> received = new ArrayList<>();

        ScriptedLlm(String... answers) {
            this.script = List.of(answers);
        }

        @Override
        public Response chat(Request r) {
            received.add(r.user());
            String out = script.get(Math.min(i++, script.size() - 1));
            return new Response(out, 100, 200, 10L, false);
        }

        @Override
        public String providerName() {
            return "scripted";
        }
    }

    /** 부르면 무조건 터지는 모델 */
    static class DeadLlm implements LlmClient {
        int calls = 0;

        @Override
        public Response chat(Request r) {
            calls++;
            throw new LlmCallException("연결 실패");
        }

        @Override
        public String providerName() {
            return "dead";
        }
    }

    /** 첫 응답만 잘린(truncated) 모델 */
    static class CutOffLlm implements LlmClient {
        private final String content;
        private int i = 0;

        CutOffLlm(String content) {
            this.content = content;
        }

        @Override
        public Response chat(Request r) {
            return i++ == 0
                    ? new Response(content, 100, 1536, 30L, true)   // 내용은 멀쩡, 잘림 표시
                    : new Response(content, 100, 200, 10L, false);
        }

        @Override
        public String providerName() {
            return "cutoff";
        }
    }

    private static String ask(String user) {
        return new MockLlmClient(0)
                .chat(LlmClient.Request.html(PromptBuilder.generate(), user)).content();
    }

    private static final String GOOD = ask("여름 데이터 이벤트");
    private static final String BAD = ask("FAIL");

    private static final RetryService.HtmlValidator GENERATED = BlockValidator::validateGenerated;

    // ── 테스트 ────────────────────────────────────────────

    @Test
    @DisplayName("한 번에 통과하면 한 번만 부른다")
    void 성공하면_재시도_안_한다() {
        ScriptedLlm llm = new ScriptedLlm(GOOD);
        RetryService.Result r = new RetryService(llm, 3).run("system", "여름 이벤트", GENERATED);

        assertTrue(r.ok());
        assertEquals(1, r.attempts(), "통과했는데 더 불렀습니다.");
        assertEquals(1, llm.received.size());
        assertNotNull(r.html());
    }

    @Test
    @DisplayName("실패하면 다시 부르고, 두 번째에 통과한다")
    void 실패하면_다시_부른다() {
        ScriptedLlm llm = new ScriptedLlm(BAD, GOOD);
        RetryService.Result r = new RetryService(llm, 3).run("system", "여름 이벤트", GENERATED);

        assertTrue(r.ok());
        assertEquals(2, r.attempts());
        assertFalse(r.traces().get(0).passed(), "1차가 통과로 기록됐습니다.");
        assertTrue(r.traces().get(1).passed());
    }

    @Test
    @DisplayName("재시도 프롬프트에 실패 '의미' 메시지가 들어간다")
    void 되먹임이_의미_메시지다() {
        ScriptedLlm llm = new ScriptedLlm(BAD, GOOD);
        new RetryService(llm, 3).run("system", "여름 이벤트", GENERATED);

        String second = llm.received.get(1);
        assertTrue(second.contains("benefits"),
                "실패 메시지가 재시도 프롬프트에 없습니다. 모델이 뭘 고쳐야 할지 모릅니다:\n" + second);
        assertTrue(second.contains("여름 이벤트"),
                "원래 요청이 빠졌습니다. 모델이 무슨 이벤트인지 모르게 됩니다.");
    }

    @Test
    @DisplayName("★ 이력을 쌓지 않는다 — 3차 입력에 1차 출력이 없다")
    void 대화를_쌓지_않는다() {
        // 1차·2차를 서로 다르게 실패시켜서, 3차 입력에 1차 흔적이 남는지 본다
        String bad1 = BAD.replace("</section>", "<!--첫번째시도흔적--></section>");
        ScriptedLlm llm = new ScriptedLlm(bad1, BAD, GOOD);

        RetryService.Result r = new RetryService(llm, 3).run("system", "여름 이벤트", GENERATED);

        assertTrue(r.ok());
        String third = llm.received.get(2);
        assertFalse(third.contains("첫번째시도흔적"),
                "3차 입력에 1차 출력이 남아 있습니다. 쌓으면 실패가 증폭됩니다(C군).");
    }

    @Test
    @DisplayName("계속 실패하면 최초 1회 + 재시도 3회 = 4회에서 멈춘다")
    void 네_번에서_멈춘다() {
        ScriptedLlm llm = new ScriptedLlm(BAD);
        RetryService.Result r = new RetryService(llm, 3).run("system", "여름 이벤트", GENERATED);

        assertFalse(r.ok());
        assertNull(r.html(), "실패인데 html 이 들어 있습니다.");
        assertEquals(4, r.attempts());
        assertEquals(4, llm.received.size());
        assertFalse(r.lastFailures().isEmpty(), "왜 실패했는지가 비었습니다. 폴백 안내를 못 만듭니다.");
    }

    @Test
    @DisplayName("호출 실패는 재시도하지 않고 그대로 던진다")
    void 호출실패는_재시도_대상이_아니다() {
        DeadLlm llm = new DeadLlm();

        assertThrows(LlmCallException.class,
                () -> new RetryService(llm, 3).run("system", "여름 이벤트", GENERATED));
        assertEquals(1, llm.calls,
                "Ollama 가 죽었는데 " + llm.calls + "번 불렀습니다. 프롬프트를 고쳐도 안 고쳐집니다.");
    }

    @Test
    @DisplayName("잘린 출력(done_reason=length)은 통과시키지 않는다")
    void 잘린_출력은_재시도한다() {
        RetryService.Result r =
                new RetryService(new CutOffLlm(GOOD), 3).run("system", "여름 이벤트", GENERATED);

        assertTrue(r.ok());
        assertEquals(2, r.attempts(), "잘린 출력을 그대로 통과시켰습니다.");
        assertTrue(r.traces().get(0).failures().stream()
                        .anyMatch(f -> f.code().equals("truncated")),
                "truncated 가 실패로 기록되지 않았습니다.");
    }

    @Test
    @DisplayName("검증 전에 정화가 걸린다")
    void 정화가_검증보다_먼저다() {
        String withScript = GOOD.replace("</section>", "<script>alert(1)</script></section>");
        ScriptedLlm llm = new ScriptedLlm(withScript);

        RetryService.Result r = new RetryService(llm, 3).run("system", "여름 이벤트", GENERATED);

        assertTrue(r.ok());
        assertFalse(r.html().contains("<script"), "정화를 안 거친 HTML 이 나왔습니다: " + r.html());
        assertTrue(r.traces().get(0).raw().contains("<script"),
                "raw 는 모델 원문이어야 합니다. 로그에 남길 게 없어집니다.");
    }

    @Test
    @DisplayName("호출 시간은 LlmClient 가 준 값을 그대로 쓴다")
    void 시간을_두_번_재지_않는다() {
        RetryService.Result r =
                new RetryService(new ScriptedLlm(GOOD), 3).run("system", "여름 이벤트", GENERATED);

        assertEquals(10L, r.traces().get(0).wallMs(),
                "Response.wallMs 가 아니라 서비스가 따로 잰 값이 들어갔습니다.");
    }
}
