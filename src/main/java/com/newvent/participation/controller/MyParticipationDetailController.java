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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "내 참여 상세", description = "로그인한 사용자 본인의 참여 기록 하나를 자세히 보여 준다. "
        + "사용자 토큰이 필요하고 관리자 토큰이면 403 (COMMON403-0)")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/participations")
public class MyParticipationDetailController {

    private final MyParticipationDetailService participationDetailService;

    @Operation(
            summary = "내 참여 상세",
            description = "참여할 때 제출한 값 (submittedData) 과 저장된 결과 (resultData) 를 그대로 준다. "
                    + "participationId 가 1 미만이면 400, 기록이 없거나 다른 사용자의 기록이면 404 (PARTICIPATION404-0)")
    @GetMapping("/{participationId}")
    public ApiResponse<MyParticipationDetailResponse> getParticipation(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable @Positive Long participationId
    ){
        MyParticipationDetailResponse response = participationDetailService.getParticipation(principal.id(), participationId);
        return ApiResponse.success(response);
    }
}
