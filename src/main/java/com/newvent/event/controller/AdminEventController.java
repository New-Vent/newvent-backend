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
import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.dto.EventDetailResponse;
import com.newvent.event.dto.EventSummaryResponse;
import com.newvent.event.service.EventService;

@Validated
@RestController
@RequestMapping("/api/admin/events")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ApiResponse<PageResponse<EventSummaryResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) OffsetDateTime periodFrom,
            @RequestParam(required = false) OffsetDateTime periodTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(
                eventService.findAdminEvents(name, status, periodFrom, periodTo, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(eventService.findAdminEvent(id));
    }
}
