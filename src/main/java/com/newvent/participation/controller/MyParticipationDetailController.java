package com.newvent.participation.controller;

import jakarta.validation.constraints.Positive;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.participation.dto.response.MyParticipationDetailResponse;
import com.newvent.participation.service.MyParticipationDetailService;

import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/participations")
public class MyParticipationDetailController {

    private final MyParticipationDetailService participationDetailService;

    @GetMapping("/{participationId}")
    public ApiResponse<MyParticipationDetailResponse> getParticipation(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable @Positive Long participationId
    ){
        MyParticipationDetailResponse response = participationDetailService.getParticipation(principal.id(), participationId);
        return ApiResponse.success(response);
    }
}
