package com.newvent.participation.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.participation.dto.response.ParticipationStatusResponse;
import com.newvent.participation.service.ParticipationStatusService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/events")
public class ParticipationStatusController {

    private final ParticipationStatusService participationStatusService;

    @GetMapping("/{eventId}/participation-status")
    public ApiResponse<ParticipationStatusResponse> getStatus(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthUser principal
    ) {
        return ApiResponse.success(
                participationStatusService.getStatus(eventId, principal.id())
        );
    }
}
