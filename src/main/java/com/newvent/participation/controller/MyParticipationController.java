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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "내 참여 목록", description = "로그인한 사용자 본인의 참여 기록만 보여 준다. "
        + "사용자 토큰이 필요하고 관리자 토큰이면 403 (COMMON403-0)")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/participations")
public class MyParticipationController {

    private final MyParticipationService myParticipationService;

    @Operation(
            summary = "내 참여 목록과 요약",
            description = "참여 현황 요약과 참여 목록을 참여일 최신순으로 페이지 단위로 준다. "
                    + "filter 는 ALL (전체) 또는 REWARDS (당첨 WON 만) 이고, 요약 수치는 filter·page 와 상관없이 전체 기록 기준이다. "
                    + "이벤트가 게시를 내렸거나 삭제됐어도 기록은 빠지지 않는다. "
                    + "filter 가 다른 값이거나 page 가 음수, size 가 1~50 밖이면 400")
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
