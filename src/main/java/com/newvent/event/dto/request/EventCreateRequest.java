package com.newvent.event.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.newvent.user.domain.MembershipGrade;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 이벤트 생성 요청.
 * 상태는 서버가 DRAFT 로 넣고, completedHtml 은 아직 만들지 않는다.
 */
public record EventCreateRequest(
        @NotBlank(message = "이벤트명은 필수입니다.")
        @Size(max = 100, message = "이벤트명은 100자 이하여야 합니다.")
        String name,

        @NotNull(message = "시작일시는 필수입니다.")
        OffsetDateTime startAt,

        @Schema(description = "시작일시보다 늦어야 한다.")
        @NotNull(message = "종료일시는 필수입니다.")
        OffsetDateTime endAt,

        /** 이헌진 템플릿 키. 없으면 null 허용. 존재·활성 여부는 서비스에서 검사. */
        @Schema(description = "기본 제공 템플릿이나 내가 등록한 활성 템플릿의 키. 생략하면 템플릿 없이 만든다.")
        String templateKey,

        /** events.grade 는 단일 컬럼이다. 없으면 NORMAL. */
        @Schema(description = "참여할 수 있는 최소 멤버십 등급. 생략하면 NORMAL")
        MembershipGrade grade
) {
}
