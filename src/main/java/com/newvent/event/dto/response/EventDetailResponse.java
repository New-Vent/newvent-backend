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
        MembershipGrade grade,
        String completedHtml,
        boolean closingSoon,
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
