package com.newvent.infra.llm;

/**
 * LLM 호출의 유일한 출입구.
 *
 * ★ generation · editor 패키지는 이 인터페이스만 봅니다.
 *
 * 구현체
 *   MockLlmClient       기본값. 키 불필요. B·C·D조가 씁니다
 *   OllamaClient        로컬 (qwen2.5:7b 등)
 *   AnthropicClient     클라우드-제거
 */
public interface LlmClient {

    Response chat(Request request);

    /** 어떤 구현체인지 — 로그·화면 표시용 */
    String providerName();


    enum Mode {
        /** HTML 생성·수정 — 출력이 길다 */
        HTML(1536),
        /** 라우터 — JSON 한 줄. 짧게 끊는다 */
        ROUTER(256);

        public final int maxTokens;
        Mode(int maxTokens) { this.maxTokens = maxTokens; }
    }

    record Request(
            String system,
            String user,
            Mode mode,
            Integer seed          // null 이면 지정 안 함
    ) {
        public static Request html(String system, String user) {
            return new Request(system, user, Mode.HTML, null);
        }
        public static Request router(String system, String user) {
            return new Request(system, user, Mode.ROUTER, null);
        }
    }

    /**
     * 토큰 수를 반드시 담습니다.
     * 명세 30행(LLM 호출 로그)이 요구하고, 비용 추적이 여기서 나옵니다.
     */
    record Response(
            String content,
            int inputTokens,
            int outputTokens,
            long wallMs,
            boolean truncated     // 상한에 걸려 잘렸나 (JSON 이면 통째로 못 씀)
    ) {}
}
