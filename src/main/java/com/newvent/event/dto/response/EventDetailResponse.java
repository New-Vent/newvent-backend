package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.user.domain.MembershipGrade;

public record EventDetailResponse(
        Long id,
        String name,
        EventStatus status,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime updatedAt,
        String template,
        String thumbnailUrl,
        MembershipGrade grade,
        String completedHtml,
        boolean closingSoon
) {
    public static EventDetailResponse from(Event event, boolean closingSoon) {
        return new EventDetailResponse(
                event.getId(),
                event.getTitle(),
                event.getStatus(),
                event.getStartDate(),
                event.getEndDate(),
                event.getUpdatedAt(),
                event.templateCode(),
                event.thumbnailPath(),
                event.getGrade(),
                event.completedHtml(),
                closingSoon);
    }
}
