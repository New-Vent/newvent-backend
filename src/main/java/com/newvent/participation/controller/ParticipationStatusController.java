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
                    + "참여할 수 없으면 unavailableReason 에 이유를 주고, 사유는 참여 API 와 같은 순서로 정해진다 — "
                    + "이미 참여 (ALREADY_PARTICIPATED) → 등급 부족 (INSUFFICIENT_GRADE) → "
                    + "참여 설정 없음 (NOT_CONFIGURED). "
                    + "참여할 수 있으면 gameCode 로 어떤 입력을 받아야 하는지 알려준다 "
                    + "(BASIC 은 추가 입력 없음, SPORTS_PREDICTION 은 prediction, "
                    + "PRE_REGISTRATION 은 phoneNumber, LUCKY_POUCH 는 pouchIndex). "
                    + "참여 설정은 개수와 내용까지 참여 API 와 같은 기준으로 본다 — "
                    + "하나가 아니거나 내용이 잘못되면 NOT_CONFIGURED 다. "
                    + "이벤트가 없으면 404 (EVENT404-0), 게시 중이 아니거나 기간 밖이면 404 (EVENT404-2) — "
                    + "참여 API 와 같은 조회를 쓰므로 판정이 같다. "
                    + "★ 그래도 canParticipate=true 가 참여 성공을 보장하지는 않는다. "
                    + "조회와 참여 사이에 관리자가 게시를 내리거나 기간이 끝나면 404 가, "
                    + "다른 요청이 먼저 참여를 기록하면 409 (PARTICIPATION409-0) 가 날 수 있다")
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
