package com.newvent.event.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record Event(
        Long id,
        String name,
        EventStatus status,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        String template,
        String thumbnailUrl,
        List<MembershipGrade> targetGrades,
        String completedHtml
) {
    public Event {
        targetGrades = targetGrades == null ? List.of() : List.copyOf(targetGrades);
    }

    public boolean deleted() {
        return deletedAt != null;
    }
}
