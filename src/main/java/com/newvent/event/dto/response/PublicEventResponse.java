package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;

public record PublicEventResponse(
        Long id,
        String title,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        EventStatus status,
        String url,
        String publishedHtml,
        boolean closingSoon) {

    public static PublicEventResponse from(Event event, boolean closingSoon) {
        String publishedHtml = event.getPublishedVersion() != null
                ? event.getPublishedVersion().getHtmlContent()
                : null;

        return new PublicEventResponse(
                event.getId(), event.getTitle(), event.getStartDate(), event.getEndDate(),
                event.getStatus(), event.getUrl(), publishedHtml, closingSoon);
    }
}
