package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.user.domain.MembershipGrade;

public record EventSummaryResponse(
        Long id,
        String name,
        EventStatus status,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime updatedAt,
        String template,
        String thumbnailHtml,
        MembershipGrade grade,
        boolean closingSoon,
        Integer publishedVersionNo,
        Integer latestVersionNo,
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
