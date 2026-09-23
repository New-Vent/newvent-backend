package com.newvent.event.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.event.dto.response.EventVersionListResponse;
import com.newvent.event.service.EventVersionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/events/{eventId}/versions")
public class EventVersionController {

    private final EventVersionService eventVersionService;

    // TODO: 인증/인가 구현 후 관리자만 조회할 수 있도록 제한
    @GetMapping
    public ApiResponse<EventVersionListResponse> getVersions(@PathVariable Long eventId) {
        return ApiResponse.success(eventVersionService.getVersions(eventId));
    }
}
