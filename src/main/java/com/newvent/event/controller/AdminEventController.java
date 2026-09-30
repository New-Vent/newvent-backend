package com.newvent.event.controller;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.dto.request.EventCreateRequest;
import com.newvent.event.dto.request.EventStatusChangeRequest;
import com.newvent.event.dto.request.EventUpdateRequest;
import com.newvent.event.dto.response.EventDetailResponse;
import com.newvent.event.dto.response.EventSummaryResponse;
import com.newvent.event.dto.response.PageResponse;
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

    @GetMapping("/trash")
    public ApiResponse<PageResponse<EventSummaryResponse>> trash(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(eventService.findDeletedEvents(page, size));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventDetailResponse>> create(
            @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody EventCreateRequest request) {
        EventDetailResponse created = eventService.create(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    /** 골격 — 본구현 전. null 필드는 변경하지 않는다. */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDetailResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody EventUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /** 소프트 삭제(deletedAt). 게시 중인 이벤트는 거부한다. */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        eventService.delete(id);
        return ResponseEntity.ok(ApiResponse.successNoData());
    }

    // 휴지통에서 복구(deletedAt 해제)
    @PostMapping("/{id}/restore")
    public ApiResponse<EventDetailResponse> restore(@PathVariable Long id) {
        return ApiResponse.success(eventService.restore(id));
    }

    /** 골격 — DRAFT → PUBLISHED → ENDED. 본구현 전. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<EventDetailResponse>> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody EventStatusChangeRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /** 골격 — DRAFT 게시(PUBLISHED). 본구현 전. */
    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<EventDetailResponse>> publish(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
