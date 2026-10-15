package com.newvent.participation.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.participation.dto.request.ParticipationConfigRequest;
import com.newvent.participation.dto.response.ParticipationConfigResponse;
import com.newvent.participation.service.ParticipationConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "참여 설정", description = "이벤트의 참여 방식과 설정. 이벤트를 만든 관리자만 쓴다 — "
        + "남의 이벤트면 403 (COMMON403-0), 없거나 휴지통에 있으면 404 (EVENT404-0). "
        + "이벤트당 설정은 하나라서 PUT 으로 덮어쓴다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/events/{eventId}/participation-config")
public class AdminParticipationConfigController {

    private final ParticipationConfigService configService;

    @Operation(
            summary = "참여 설정 저장",
            description = "이벤트당 하나라서 있으면 덮어쓰고 없으면 만든다. 저장 전에 방식별로 설정을 검증한다 — "
                    + "모양이 맞지 않으면 409 (PARTICIPATION409-1). "
                    + "없거나 비활성인 방식이면 409 (PARTICIPATION409-2). "
                    + "★ 참여 기록이 있으면 **이미 결정된 결과를 뒤집는 값**은 바꿀 수 없다 "
                    + "(409 PARTICIPATION409-3) — gameCode · resultMode · winProbability · "
                    + "winnerCount · predictionOptions · pouches 가 그렇다. "
                    + "반대로 prizeName(경품명 오타 수정) · announcementAt(일정 연기) 는 "
                    + "참여 기록이 있어도 바꿀 수 있다 — "
                    + "그 시점의 참여는 모두 결과 미정(PENDING)이기 때문이다. "
                    + "누가 무엇을 바꿨는지는 서버 로그(INFO)에 남는다.")
    @PutMapping
    public ApiResponse<ParticipationConfigResponse> save(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthUser admin,
            @Valid @RequestBody ParticipationConfigRequest request) {
        return ApiResponse.success(configService.save(
                eventId, admin, request.gameCode(), request.configOrEmpty()));
    }

    @Operation(
            summary = "참여 설정 조회",
            description = "아직 설정하지 않았으면 404 가 아니라 gameCode=null, config={} 를 준다 — "
                    + "설정 화면은 빈 폼으로 시작하므로 분기를 만들 필요가 없다.")
    @GetMapping
    public ApiResponse<ParticipationConfigResponse> find(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthUser admin) {
        return ApiResponse.success(configService.find(eventId, admin));
    }
}
