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
import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.EventProgress;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.dto.request.EventCreateRequest;
import com.newvent.event.dto.request.EventPublishRequest;
import com.newvent.event.dto.request.EventStatusChangeRequest;
import com.newvent.event.dto.request.EventUpdateRequest;
import com.newvent.event.dto.response.EventCountsResponse;
import com.newvent.event.dto.response.EventDetailResponse;
import com.newvent.event.dto.response.EventSummaryResponse;
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
            @RequestParam(required = false) EventProgress progress,
            @RequestParam(required = false) OffsetDateTime periodFrom,
            @RequestParam(required = false) OffsetDateTime periodTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(
                eventService.findAdminEvents(name, status, progress, periodFrom, periodTo, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(eventService.findAdminEvent(id));
    }

    // 목록 화면 상단 카운트(전체/미게시/진행중/종료). 삭제된 건 제외.
    @GetMapping("/counts")
    public ApiResponse<EventCountsResponse> counts() {
        return ApiResponse.success(eventService.findEventCounts());
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

    // null 필드는 변경하지 않는다.
    @PatchMapping("/{id}")
    public ApiResponse<EventDetailResponse> update(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable Long id,
            @Valid @RequestBody EventUpdateRequest request) {
        return ApiResponse.success(eventService.update(principal.id(), id, request));
    }

    // 소프트 삭제(deletedAt). 게시 중인 이벤트는 거부한다.
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

    // 휴지통에서 영구 삭제. 복구 불가
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<ApiResponse<Void>> hardDelete(@PathVariable Long id) {
        eventService.hardDelete(id);
        return ResponseEntity.ok(ApiResponse.successNoData());
    }

    // 종료(PUBLISHED → ENDED)만 받는다. 게시는 POST /{id}/publish
    @PatchMapping("/{id}/status")
    public ApiResponse<EventDetailResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody EventStatusChangeRequest request) {
        return ApiResponse.success(eventService.changeStatus(id, request.status()));
    }

    // 게시(DRAFT→PUBLISHED) 또는 재게시(다른 버전으로 교체)
    @PostMapping("/{id}/publish")
    public ApiResponse<EventDetailResponse> publish(
            @PathVariable Long id,
            @Valid @RequestBody EventPublishRequest request) {
        return ApiResponse.success(eventService.publish(id, request.versionId()));
    }

    // 게시 내리기(PUBLISHED → DRAFT) 종료는 PATCH /{id}/status
    @PostMapping("/{id}/unpublish")
    public ApiResponse<EventDetailResponse> unpublish(@PathVariable Long id) {
        return ApiResponse.success(eventService.unpublish(id));
    }
}
