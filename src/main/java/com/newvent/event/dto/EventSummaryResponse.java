package com.newvent.event.dto;

import java.time.OffsetDateTime;
import java.util.List;

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
        List<MembershipGrade> targetGrades,
        boolean closingSoon
) {
    public static EventSummaryResponse from(Event event, boolean closingSoon) {
        return new EventSummaryResponse(
                event.getId(),
                event.getTitle(),
                event.getStatus(),
                event.getStartDate(),
                event.getEndDate(),
                event.getUpdatedAt(),
                event.templateCode(),
                event.thumbnailPath(),
                event.targetGrades(),
                closingSoon);
    }
}
