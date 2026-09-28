package com.newvent.infra.llm;

/**
 * 모델을 부르는 유일한 문. {@link LlmClient} 를 서비스가 직접 들지 않게 한다.
 *
 * 문에서 하는 일은 셋이다.
 *   ① {@link #reserve(int)}  — 하루 상한을 부르기 전에 본다
 *   ② {@link #call}          — 부른다
 *   ③ 호출이 터지면          — llm_call_logs 에 실패 행을 남기고 그대로 다시 던진다
 */
public interface LlmCallGateway {

    /**
     * 앞으로 쓸 호출 수만큼 여유가 있는지 본다.
     *
     * 상한을 넘으면 {@code LlmDailyLimitExceededException} — 429 로 나간다.
     */
    void reserve(int expectedCalls);

    /**
     * 부른다.
     *
     * 호출 자체가 실패하면 {@link LlmCallException} — <b>실패 행을 남긴 뒤 그대로 올린다.</b>
     */
    LlmClient.Response call(LlmCallContext ctx, LlmClient.Request req);

    /**
     * 상한도 기록도 없이 모델만 부른다. 테스트 전용.
     *
     * ★ 빈으로 등록하지 않는다 — 애너테이션이 없으니 스캔에 안 걸린다.
     *   운영 경로에서 쓰이면 호출이 로그에 안 남고 상한도 안 걸린다.
     */
    final class Direct implements LlmCallGateway {

        private final LlmClient llm;

        public Direct(LlmClient llm) {
            this.llm = llm;
        }

        @Override
        public void reserve(int expectedCalls) {
            // 상한 없음
        }

        @Override
        public LlmClient.Response call(LlmCallContext ctx, LlmClient.Request req) {
            return llm.chat(req);
        }
    }
}
