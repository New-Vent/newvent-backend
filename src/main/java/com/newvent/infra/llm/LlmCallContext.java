package com.newvent.infra.llm;

import java.util.UUID;

/**
 * 모델 호출 한 번을 <b>llm_call_logs 의 한 행</b>에 붙일 수 있게 하는 꼬리표.
 *
 */
public record LlmCallContext(Long eventId, UUID requestId, int attemptNo) {

    public LlmCallContext {
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo 는 1부터입니다: " + attemptNo);
        }
    }

    /** 묶음의 첫 시도 */
    public static LlmCallContext of(Long eventId, UUID requestId) {
        return new LlmCallContext(eventId, requestId, 1);
    }

    /** 붙일 이벤트가 없는 호출 — 기록하지 않는다 */
    public static LlmCallContext anonymous() {
        return new LlmCallContext(null, null, 1);
    }

    /** 같은 묶음의 n 번째 시도 */
    public LlmCallContext attempt(int n) {
        return new LlmCallContext(eventId, requestId, n);
    }

    /** 행을 만들 수 있는가 — event_id NOT NULL 이라 eventId 가 없으면 못 만든다 */
    public boolean recordable() {
        return eventId != null && requestId != null;
    }
}
