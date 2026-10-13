package com.newvent.participation.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.participation.dto.request.SportsResultRequest;
import com.newvent.participation.service.SportsPredictionResultService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 스포츠 예측 결과")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/events")
public class AdminSportsResultController {

    private final SportsPredictionResultService resultService;

    @Operation(
        summary = "실제 경기 결과 등록·정정",
        description = "이벤트 소유자가 종료 전에도 정답을 미리 등록할 수 있습니다. "
            + "당첨 결과는 이벤트 종료 후 발표 시각에 처리하며, "
            + "발표 처리 완료 후에는 정답을 변경할 수 없습니다."
    )
    @PutMapping("/{eventId}/sports-result")
    public ApiResponse<Void> registerResult(
        @AuthenticationPrincipal AuthUser admin,
        @PathVariable Long eventId,
        @Valid @RequestBody SportsResultRequest request
    ) {
        resultService.registerResult(eventId, admin, request.winningPrediction());
        return ApiResponse.successNoData();
    }
}
