package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record PublicEventSummaryResponse(
        Long id,
        String title,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        EventStatus status,
        @Schema(description = "템플릿 코드. 목록의 category 필터에 쓰는 값")
        String category,
        @Schema(description = "마감임박. 이미 시작했고 종료 3일 전부터 종료 시각까지면 true")
        boolean closingSoon,
        @Schema(description = "썸네일용 hero 블록 HTML 조각. 게시 버전이 없으면 null. 격리된 iframe 으로 그린다.")
        String thumbnailHtml) {

    // @param thumbnailHtml 게시 HTML 에서 hero 블록만 잘라 슬롯을 채운 조각. 게시 버전이 없으면 null (PublicEventService.thumbnailHtmlOf 가 만든다)
    public static PublicEventSummaryResponse from(Event event, boolean closingSoon, String thumbnailHtml) {
        return new PublicEventSummaryResponse(
                event.getId(), event.getTitle(), event.getStartDate(), event.getEndDate(),
                event.getStatus(), event.templateCode(), closingSoon, thumbnailHtml);
    }
}
