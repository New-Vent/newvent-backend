package com.newvent.event.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.newvent.user.domain.MembershipGrade;

/**
 * 관리자 이벤트 부분 수정 요청.
 * null 필드는 변경하지 않는다. templateKey 가 빈 문자열이면 템플릿을 해제한다.
 */
public record EventUpdateRequest(
        /** null 이면 유지. 값이 오면 생성 요청의 @NotBlank 와 같은 기준으로 공백만 있는 이름을 거부한다. */
        @Pattern(regexp = "(?s).*\\S.*", message = "이벤트명은 공백만으로 입력할 수 없습니다.")
        @Size(max = 100, message = "이벤트명은 100자 이하여야 합니다.")
        String name,

        OffsetDateTime startAt,

        OffsetDateTime endAt,

        String templateKey,

        /** events.grade 는 단일 컬럼이다. */
        MembershipGrade grade
) {
}
