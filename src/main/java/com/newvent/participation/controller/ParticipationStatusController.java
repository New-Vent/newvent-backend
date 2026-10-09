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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "참여 가능 여부", description = "이벤트 화면에서 참여 버튼 상태를 정할 때 쓴다. "
        + "사용자 토큰이 필요하고 관리자 토큰이면 403 (COMMON403-0)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/events")
public class ParticipationStatusController {

    private final ParticipationStatusService participationStatusService;

    @Operation(
            summary = "참여 가능 여부 조회",
            description = "이 이벤트에 이미 참여했는지와 지금 참여할 수 있는지를 준다. "
                    + "참여할 수 없으면 unavailableReason 에 이유를 주고, 이미 참여 (ALREADY_PARTICIPATED) 가 "
                    + "등급 부족 (INSUFFICIENT_GRADE) 보다 앞선다. "
                    + "게시 상태와 참여 설정은 보지 않으므로 canParticipate 가 true 여도 참여가 404·409 로 실패할 수 있다. "
                    + "이벤트가 없으면 404 (EVENT404-0), 기간 밖이면 404 (EVENT404-2)")
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
