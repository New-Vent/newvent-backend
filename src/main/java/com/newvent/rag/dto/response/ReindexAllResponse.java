package com.newvent.rag.dto.response;

import java.util.List;

// 전체 재색인 결과. 실패해도 멈추지 않고 다음 이벤트로 넘어간다
public record ReindexAllResponse(int totalEvents, int succeeded,
		List<Long> failedEventIds, int totalChunks) {

}
