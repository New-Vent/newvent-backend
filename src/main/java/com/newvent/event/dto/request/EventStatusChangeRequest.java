package com.newvent.event.dto.request;

import jakarta.validation.constraints.NotNull;

import com.newvent.event.domain.EventStatus;

/**
 * 이벤트 상태 변경 요청. (골격 — 본구현 전)
 * DRAFT → PUBLISHED → ENDED 단방향.
 */
public record EventStatusChangeRequest(
        @NotNull(message = "상태는 필수입니다.")
        EventStatus status
) {
}
