package com.newvent.event.controller;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventProgress;
import com.newvent.event.dto.response.PageResponse;
import com.newvent.event.dto.response.PublicEventResponse;
import com.newvent.event.dto.response.PublicEventSummaryResponse;
import com.newvent.event.service.PublicEventService;

@Validated
@RestController
@RequestMapping("/api/public/events")
public class PublicEventController {

    private final PublicEventService publicEventService;

    public PublicEventController(PublicEventService publicEventService) {
        this.publicEventService = publicEventService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PublicEventSummaryResponse>> getEvents(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EventProgress progress,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(publicEventService.getPublicEvents(category, keyword, progress, page, size));
    }

    @GetMapping("/{eventId}")
    public ApiResponse<PublicEventResponse> getEvent(@PathVariable Long eventId) {
        Event event = publicEventService.getPublicEvent(eventId);
        boolean closingSoon = publicEventService.isClosingSoon(event, OffsetDateTime.now());
        // 슬롯을 채운 HTML 을 서비스에서 받아 넘긴다. 엔티티에서 직접 꺼내면 기간이 빈 채로 나간다
        return ApiResponse.success(PublicEventResponse.from(
                event, closingSoon, publicEventService.publishedHtmlOf(event)));
    }
}
