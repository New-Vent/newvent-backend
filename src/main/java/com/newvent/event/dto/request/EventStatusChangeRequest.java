package com.newvent.event.dto.request;

import jakarta.validation.constraints.NotNull;

import com.newvent.event.domain.EventStatus;

/**
 * 이벤트 상태 변경 요청.
 * 지금은 종료(ENDED)만 받는다. 게시(PUBLISHED)는 게시 API 로 한다.
 */
public record EventStatusChangeRequest(
        @NotNull(message = "상태는 필수입니다.")
        EventStatus status
) {
}
