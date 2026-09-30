package com.newvent.participation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.participation.dto.response.ParticipationCreateResponse;
import com.newvent.participation.service.ParticipationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/events")
public class ParticipationController {

    private final ParticipationService participationService;

    @PostMapping("/{eventId}/participations")
    public ResponseEntity<ApiResponse<ParticipationCreateResponse>> participate(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthUser principal
    ) {
        ParticipationCreateResponse response = participationService.participate(eventId, principal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
