package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.user.domain.MembershipGrade;

import io.swagger.v3.oas.annotations.media.Schema;

public record EventDetailResponse(
        Long id,
        String name,
        EventStatus status,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime updatedAt,
        @Schema(description = "템플릿 키. 템플릿 없이 만들었으면 null")
        String template,
        @Schema(description = "참여할 수 있는 최소 멤버십 등급")
        MembershipGrade grade,
        @Schema(description = "게시 버전의 HTML. 게시한 적 없으면 null")
        String completedHtml,
        @Schema(description = "마감임박. 게시 중이고 이미 시작했으며 종료 3일 전부터 종료 시각까지면 true")
        boolean closingSoon,
        @Schema(description = "요청한 관리자가 만든 이벤트면 true. 수정·게시·삭제 버튼 노출 기준")
        boolean ownedByMe
) {
    public static EventDetailResponse from(Event event, boolean closingSoon, boolean ownedByMe) {
        return new EventDetailResponse(
                event.getId(),
                event.getTitle(),
                event.getStatus(),
                event.getStartDate(),
                event.getEndDate(),
                event.getUpdatedAt(),
                event.templateCode(),
                event.getGrade(),
                event.completedHtml(),
                closingSoon,
                ownedByMe);
    }
}
