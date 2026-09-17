package com.newvent.infra.llm;

/**
 * LLM 호출 자체가 실패했다. **검증 실패와 다르다.**
 *
 *   LlmCallException   연결 안 됨 · 타임아웃 · 500 · 응답이 JSON 이 아님
 *                      → 재시도해도 모델에게 할 말이 없다. 사용자에게 "지금 안 됩니다" 를 보인다
 *
 *   검증 실패(Failure) → 모델이 답은 했는데 형태가 틀렸다
 *                      → 뭐가 틀렸는지 말해주고 다시 시킨다 (RetryService)
 *
 * 둘을 같은 걸로 다루면 "Ollama 가 안 떠서" 를 3번 재시도하며 기다리게 된다.
 */
public class LlmCallException extends RuntimeException {

    public LlmCallException(String message) {
        super(message);
    }

    public LlmCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
