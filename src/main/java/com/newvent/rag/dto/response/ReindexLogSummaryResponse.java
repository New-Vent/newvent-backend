package com.newvent.rag.dto.response;

import java.time.Instant;

import com.newvent.rag.domain.RagReindexLog;

/**
 * 재색인 이력 한 줄. 대시보드의 재색인 이력 카드와 추이 툴팁이 읽는다.
 * scope: SINGLE(버전 하나) / EVENT(이벤트 전체 버전) / ALL(전체 재색인)
 */
public record ReindexLogSummaryResponse(
        Long id,
        String scope,
        Long eventId,
        Long versionId,
        String requestedBy,
        Integer chunkCount,
        Integer durationMs,
        String status,
        String failureMessage,
        Instant createdAt
) {
    public static ReindexLogSummaryResponse from(RagReindexLog l) {
        return new ReindexLogSummaryResponse(
                l.getId(),
                l.getScope(),
                l.getEventId(),
                l.getVersionId(),
                l.getRequestedBy(),
                l.getChunkCount(),
                l.getDurationMs(),
                l.getStatus(),
                l.getFailureMessage(),
                l.getCreatedAt()
        );
    }
}
