package com.newvent.participation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.participation.dto.request.ParticipationCreateRequest;
import com.newvent.participation.dto.response.ParticipationCreateResponse;
import com.newvent.participation.service.ParticipationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "이벤트 참여", description = "로그인한 사용자가 이벤트에 참여한다. 이벤트마다 한 번만 참여할 수 있다. "
        + "사용자 토큰이 필요하고 관리자 토큰이면 403 (COMMON403-0)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/events")
public class ParticipationController {

    private final ParticipationService participationService;

    @Operation(
            summary = "이벤트 참여",
            description = "게시 중인 이벤트에 참여하고 201 로 결과를 준다. 본문에는 참여 방식에 맞는 값 하나만 보낸다 "
                    + "(스포츠 예측형 prediction, 사전예약형 phoneNumber, 복주머니형 pouchIndex, 기본형은 {} 또는 본문 없이). "
                    + "즉시 추첨이면 WON·LOST, 결과를 나중에 정하면 PENDING, 결과가 없는 방식이면 {} 가 저장된다. "
                    + "값이 없거나 틀리거나 참여 방식과 상관없는 값이 있으면 400 (PARTICIPATION400-0). "
                    + "이벤트가 없으면 404 (EVENT404-0), 기간 밖이거나 게시 중이 아니면 404 (EVENT404-2), "
                    + "이미 참여했으면 409 (PARTICIPATION409-0), 등급이 모자라면 403 (PARTICIPATION403-0), "
                    + "참여 설정이 없거나 잘못됐으면 409 (PARTICIPATION409-1), 지원하지 않거나 꺼진 참여 방식이면 409 (PARTICIPATION409-2)")
    @PostMapping("/{eventId}/participations")
    public ResponseEntity<ApiResponse<ParticipationCreateResponse>> participate(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody(required = false) ParticipationCreateRequest request
    ) {
        ParticipationCreateResponse response = participationService.participate(eventId, principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
