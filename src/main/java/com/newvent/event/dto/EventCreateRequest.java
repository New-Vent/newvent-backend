package com.newvent.event.dto;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.newvent.event.domain.MembershipGrade;

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

        @NotNull(message = "종료일시는 필수입니다.")
        OffsetDateTime endAt,

        /** 이헌진 템플릿 키. 없으면 null 허용. 존재·활성 여부는 서비스에서 검사. */
        String templateKey,

        /** 게시 전에는 비어 있어도 됨. 게시 시 1개 이상 필요. */
        List<MembershipGrade> targetGrades
) {
}
