package com.newvent.generation.dto;

import java.time.Instant;
import java.util.UUID;

import com.newvent.generation.domain.LlmCallLog;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "RAG 참고 예시를 프롬프트에 넣었으면 true")
        boolean ragUsed,
        @Schema(description = "참고한 RAG 청크 ID 를 쉼표로 이은 문자열 (배열 아님). ragUsed 가 false 면 null",
                example = "41,42,43")
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
