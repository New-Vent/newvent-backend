package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.user.domain.MembershipGrade;

public record PublicEventResponse(
        Long id,
        String title,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        EventStatus status,
        MembershipGrade grade,
        String url,
        String publishedHtml,
        boolean closingSoon) {

    public static PublicEventResponse from(Event event, boolean closingSoon) {
        String publishedHtml = event.getPublishedVersion() != null
                ? event.getPublishedVersion().getHtmlContent()
                : null;

        return new PublicEventResponse(
                event.getId(), event.getTitle(), event.getStartDate(), event.getEndDate(),
                event.getStatus(), event.getGrade(), event.getUrl(), publishedHtml, closingSoon);
    }
}
