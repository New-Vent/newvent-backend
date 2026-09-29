package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;

public record PublicEventSummaryResponse(
        Long id,
        String title,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        EventStatus status,
        String category,
        boolean closingSoon) {

    public static PublicEventSummaryResponse from(Event event, boolean closingSoon) {
        return new PublicEventSummaryResponse(
                event.getId(), event.getTitle(), event.getStartDate(), event.getEndDate(),
                event.getStatus(), event.templateCode(), closingSoon);
    }
}
