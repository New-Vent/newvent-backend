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
        String thumbnailUrl,
        MembershipGrade grade,
        boolean closingSoon,
        Integer publishedVersionNo,
        Integer latestVersionNo
) {
    /** latestVersionNo 는 목록 한 페이지를 한 번에 모아 온 값이다. 버전이 없으면 null. */
    public static EventSummaryResponse from(Event event, boolean closingSoon, Integer latestVersionNo) {
        return new EventSummaryResponse(
                event.getId(),
                event.getTitle(),
                event.getStatus(),
                event.getStartDate(),
                event.getEndDate(),
                event.getUpdatedAt(),
                event.templateCode(),
                event.thumbnailPath(),
                event.getGrade(),
                closingSoon,
                event.getPublishedVersion() == null ? null : event.getPublishedVersion().getVersionNo(),
                latestVersionNo);
    }
}
