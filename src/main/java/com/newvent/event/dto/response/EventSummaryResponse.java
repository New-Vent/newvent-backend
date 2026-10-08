package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.user.domain.MembershipGrade;

import io.swagger.v3.oas.annotations.media.Schema;

public record EventSummaryResponse(
        Long id,
        String name,
        EventStatus status,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime updatedAt,
        @Schema(description = "템플릿 키. 템플릿 없이 만들었으면 null")
        String template,
        @Schema(description = "썸네일용 hero 블록 HTML 조각. 게시 버전, 없으면 최신 버전에서 자른다. 버전이나 hero 가 없으면 null. "
                + "격리된 iframe 으로 그린다.")
        String thumbnailHtml,
        @Schema(description = "참여할 수 있는 최소 멤버십 등급")
        MembershipGrade grade,
        @Schema(description = "마감임박. 게시 중이고 이미 시작했으며 종료 3일 전부터 종료 시각까지면 true")
        boolean closingSoon,
        @Schema(description = "게시 중인 버전 번호. 게시한 적 없으면 null")
        Integer publishedVersionNo,
        @Schema(description = "가장 최근 버전 번호. 버전이 없으면 null. publishedVersionNo 보다 크면 게시 후 새 버전이 있다.")
        Integer latestVersionNo,
        @Schema(description = "요청한 관리자가 만든 이벤트면 true. 수정·게시·삭제 버튼 노출 기준")
        boolean ownedByMe
) {
	// latestVersionNo 는 목록 한 페이지를 한 번에 모아 온 값 - 버전이 없으면 null
    // thumbnailHtml 은 hero 블록만 담은 조각이다(게시 버전, 없으면 최신 버전). 버전이 없거나 hero 가 없으면 null
    public static EventSummaryResponse from(
            Event event, boolean closingSoon, Integer latestVersionNo, String thumbnailHtml, boolean ownedByMe) {
        return new EventSummaryResponse(
                event.getId(),
                event.getTitle(),
                event.getStatus(),
                event.getStartDate(),
                event.getEndDate(),
                event.getUpdatedAt(),
                event.templateCode(),
                thumbnailHtml,
                event.getGrade(),
                closingSoon,
                event.getPublishedVersion() == null ? null : event.getPublishedVersion().getVersionNo(),
                latestVersionNo,
                ownedByMe);
    }
}
