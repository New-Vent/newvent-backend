package com.newvent.event.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.MembershipGrade;

public record EventDetailResponse(
        Long id,
        String name,
        EventStatus status,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime updatedAt,
        String template,
        String thumbnailUrl,
        List<MembershipGrade> targetGrades,
        String completedHtml,
        boolean closingSoon
) {
    public static EventDetailResponse from(Event event, boolean closingSoon) {
        return new EventDetailResponse(
                event.id(),
                event.name(),
                event.status(),
                event.startAt(),
                event.endAt(),
                event.updatedAt(),
                event.template(),
                event.thumbnailUrl(),
                event.targetGrades(),
                event.completedHtml(),
                closingSoon);
    }
}
