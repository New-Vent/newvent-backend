package com.newvent.event.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import com.newvent.common.response.ApiResponse;
import com.newvent.event.dto.request.DirectEditRequest;
import com.newvent.event.dto.response.DirectEditResponse;
import com.newvent.event.dto.response.EventVersionDetailResponse;
import com.newvent.event.dto.response.EventVersionListResponse;
import com.newvent.event.service.DirectEditService;
import com.newvent.event.service.EventVersionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/events/{eventId}/versions")
public class EventVersionController {

    private final EventVersionService eventVersionService;
    private final DirectEditService directEditService;

    @PostMapping("/direct-edit")
    public ApiResponse<DirectEditResponse> directEdit(
            @PathVariable Long eventId,
            @Valid @RequestBody DirectEditRequest request
    ) {
        return ApiResponse.success(directEditService.directEdit(eventId, request));
    }

    // TODO: 인증/인가 구현 후 관리자만 조회할 수 있도록 제한
    @GetMapping
    public ApiResponse<EventVersionListResponse> getVersions(@PathVariable Long eventId) {
        return ApiResponse.success(eventVersionService.getVersions(eventId));
    }

    @PutMapping("/{versionId}/checkpoint")
    public ApiResponse<Void> markCheckpoint(
            @PathVariable Long eventId,
            @PathVariable Long versionId
    ) {
        eventVersionService.markCheckpoint(eventId, versionId);
        return ApiResponse.successNoData();
    }

    @DeleteMapping("/{versionId}/checkpoint")
    public ApiResponse<Void> unmarkCheckpoint(
            @PathVariable Long eventId,
            @PathVariable Long versionId
    ) {
        eventVersionService.unmarkCheckpoint(eventId, versionId);
        return ApiResponse.successNoData();
    }

    @GetMapping("/{versionId}")
    public ApiResponse<EventVersionDetailResponse> getVersion(
            @PathVariable Long eventId,
            @PathVariable Long versionId
    ) {
        return ApiResponse.success(eventVersionService.getVersion(eventId, versionId));
    }
}
