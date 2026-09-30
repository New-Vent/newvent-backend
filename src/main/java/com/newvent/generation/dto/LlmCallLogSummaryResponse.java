package com.newvent.generation.dto;

import java.time.Instant;
import java.util.UUID;

import com.newvent.generation.domain.LlmCallLog;

/** 호출 로그 목록 행. 상세와 달리 실패 원문(failure*)·잘림·chunkIds 는 뺀다. */
public record LlmCallLogSummaryResponse(
        Long id,
        Long eventId,
        UUID requestId,
        Integer attemptNo,
        String modelName,
        String provider,
        boolean callOk,
        boolean validOk,
        Integer inputTokens,
        Integer outputTokens,
        Integer responseTimeMs,
        boolean ragUsed,
        Instant createdAt
) {
    public static LlmCallLogSummaryResponse from(LlmCallLog l) {
        return new LlmCallLogSummaryResponse(
                l.getId(), l.getEvent().getId(), l.getRequestId(), l.getAttemptNo(),
                l.getModelName(), l.getProvider(), l.isCallOk(), l.isValidOk(),
                l.getInputTokens(), l.getOutputTokens(), l.getResponseTimeMs(),
                l.isRagUsed(), l.getCreatedAt());
    }
}