package com.newvent.generation.dto;

import java.time.Instant;
import java.util.UUID;

import com.newvent.generation.domain.LlmCallLog;

/** 호출 로그 상세. 목록에 없는 실패 분류·잘림·RAG 청크 아이디까지 내려간다. */
public record LlmCallLogDetailResponse(
        Long id,
        Long eventId,
        Long versionId,
        UUID requestId,
        Integer attemptNo,
        String modelName,
        String provider,
        boolean callOk,
        boolean validOk,
        String failureType,
        String failureMessage,
        String failCodes,
        boolean truncated,
        Integer inputTokens,
        Integer outputTokens,
        Integer responseTimeMs,
        boolean ragUsed,
        String chunkIds,
        Instant createdAt
) {
    public static LlmCallLogDetailResponse from(LlmCallLog l) {
        return new LlmCallLogDetailResponse(
                l.getId(), l.getEvent().getId(),
                l.getVersion() == null ? null : l.getVersion().getId(),
                l.getRequestId(), l.getAttemptNo(), l.getModelName(), l.getProvider(),
                l.isCallOk(), l.isValidOk(),
                l.getFailureType() == null ? null : l.getFailureType().name(),
                l.getFailureMessage(), l.getFailCodes(), l.isTruncated(),
                l.getInputTokens(), l.getOutputTokens(), l.getResponseTimeMs(),
                l.isRagUsed(), l.getChunkIds(), l.getCreatedAt());
    }
}