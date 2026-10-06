package com.newvent.participation.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.participation.dto.request.ParticipationListFilter;
import com.newvent.participation.dto.response.MyParticipationListResponse;
import com.newvent.participation.service.MyParticipationService;

import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/participations")
public class MyParticipationController {

    private final MyParticipationService myParticipationService;

    @GetMapping
    public ApiResponse<MyParticipationListResponse> getParticipations(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(defaultValue = "ALL")
            ParticipationListFilter filter,
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,
            @RequestParam(defaultValue = "10")
            @Min(1)
            @Max(50)
            int size
    ) {
        MyParticipationListResponse response = myParticipationService.getParticipations(principal.id(), filter, page, size);
        return ApiResponse.success(response);
    }
}
