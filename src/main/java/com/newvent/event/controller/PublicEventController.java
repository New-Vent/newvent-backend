package com.newvent.event.controller;

import java.time.OffsetDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.dto.response.PublicEventResponse;
import com.newvent.event.service.PublicEventService;

@RestController
@RequestMapping("/api/public/events")
public class PublicEventController {

    private final PublicEventService publicEventService;

    public PublicEventController(PublicEventService publicEventService) {
        this.publicEventService = publicEventService;
    }

    @GetMapping("/{eventId}")
    public ApiResponse<PublicEventResponse> getEvent(@PathVariable Long eventId) {
        Event event = publicEventService.getPublicEvent(eventId);
        boolean closingSoon = publicEventService.isClosingSoon(event, OffsetDateTime.now());
        return ApiResponse.success(PublicEventResponse.from(event, closingSoon));
    }
}
