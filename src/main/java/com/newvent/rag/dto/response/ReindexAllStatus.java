package com.newvent.rag.dto.response;

import java.time.Instant;

// 전체 재색인 진행 상태. 전역 작업은 한 번에 하나뿐이라 상태도 하나면 된다.
// POST /reindex/all 이 202 로 돌려주고 GET /reindex/all/status 가 같은 모양을 돌려준다.
public record ReindexAllStatus(boolean running, Instant startedAt,
		Instant finishedAt, ReindexAllResponse lastResult) {

	public static ReindexAllStatus idle() {
		return new ReindexAllStatus(false, null, null, null);
	}
}
