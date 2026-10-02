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
        boolean closingSoon,
        String thumbnailHtml) {

    // @param thumbnailHtml 게시 HTML 에서 hero 블록만 잘라 슬롯을 채운 조각. 게시 버전이 없으면 null (PublicEventService.thumbnailHtmlOf 가 만든다)
    public static PublicEventSummaryResponse from(Event event, boolean closingSoon, String thumbnailHtml) {
        return new PublicEventSummaryResponse(
                event.getId(), event.getTitle(), event.getStartDate(), event.getEndDate(),
                event.getStatus(), event.templateCode(), closingSoon, thumbnailHtml);
    }
}
