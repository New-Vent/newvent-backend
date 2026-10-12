package com.newvent.event.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.newvent.user.domain.MembershipGrade;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 이벤트 부분 수정 요청.
 * null 필드는 변경하지 않는다. templateKey 가 빈 문자열이면 템플릿을 해제한다.
 */
public record EventUpdateRequest(
        /** null 이면 유지. 값이 오면 생성 요청의 @NotBlank 와 같은 기준으로 공백만 있는 이름을 거부한다. */
        @Schema(description = "생략하면 그대로 둔다. 공백만 있는 이름은 400, 앞뒤 공백은 지우고 저장한다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "이벤트명은 공백만으로 입력할 수 없습니다.")
        @Size(max = 100, message = "이벤트명은 100자 이하여야 합니다.")
        String name,

        @Schema(description = "생략하면 그대로 둔다.")
        OffsetDateTime startAt,

        @Schema(description = "생략하면 그대로 둔다. 바뀐 기간(보낸 값 + 기존 값)의 종료일시가 시작일시보다 늦어야 한다.")
        OffsetDateTime endAt,

        @Schema(description = "생략하면 그대로 두고, 빈 문자열이면 템플릿을 해제한다. 값이 있으면 활성 템플릿이어야 한다.")
        String templateKey,

        /** events.grade 는 단일 컬럼이다. */
        @Schema(description = "참여할 수 있는 최소 멤버십 등급. 생략하면 그대로 둔다.")
        MembershipGrade grade
) {
}
