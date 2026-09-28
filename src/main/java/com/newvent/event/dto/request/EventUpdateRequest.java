package com.newvent.event.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Size;

import com.newvent.user.domain.MembershipGrade;

/**
 * 관리자 이벤트 부분 수정 요청. (골격 — 본구현 전)
 * null 필드는 변경하지 않는다.
 */
public record EventUpdateRequest(
        @Size(min = 1, max = 100, message = "이벤트명은 1~100자여야 합니다.")
        String name,

        OffsetDateTime startAt,

        OffsetDateTime endAt,

        String templateKey,

        /** events.grade 는 단일 컬럼이다. */
        MembershipGrade grade
) {
}
