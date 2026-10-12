package com.newvent.rag.dto.response;

import java.util.List;

// 전체 재색인 결과. 실패해도 멈추지 않고 다음 이벤트로 넘어간다
// 실패 건은 ID만이 아니라 사유까지 담는다 — 로그만 뒤지면 원인 파악이 안 되기 때문
public record ReindexAllResponse(int totalEvents, int succeeded,
		List<FailedEvent> failedEvents, int totalChunks) {

	public record FailedEvent(Long eventId, String error) {}
}
