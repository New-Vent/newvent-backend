package com.newvent.rag.dto.response;

import java.time.Instant;

/** 색인 현황. pendingVersions = 한 번도 안들어간 버전 수 */
public record IndexStatusResponse(Long eventId, long totalVersions, long indexedVersions, 
		long pendingVersions, long chunkCount, Instant lastIndexedAt) {

}
