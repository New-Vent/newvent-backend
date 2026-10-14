package com.newvent.participation.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.participation.domain.ParticipationResultStatus;
import com.newvent.participation.dto.response.EventParticipantListResponse;
import com.newvent.participation.service.EventParticipantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "참여자 조회", description = "이벤트에 누가 참여했고 누가 당첨됐나. 이벤트를 만든 관리자만 쓴다 — "
        + "남의 이벤트면 403 (COMMON403-0), 없거나 휴지통에 있으면 404 (EVENT404-0). "
        + "★ 참여 입력에 전화번호가 들어갈 수 있어 개인정보다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/events/{eventId}/participations")
public class AdminEventParticipantController {

    private final EventParticipantService participantService;

    @Operation(
            summary = "참여자 목록",
            description = "참여 기록을 최신순으로 준다. resultStatus 로 거를 수 있다 — "
                    + "당첨자 명단은 resultStatus=WON 이다. "
                    + "summary 는 필터와 무관하게 전체를 세므로, 필터를 걸어도 전체 참여 수가 함께 보인다. "
                    + "participants.content[].submittedData 는 참여 방식마다 모양이 다르다 "
                    + "(기본형 {}, 스포츠 예측형 prediction, 사전예약형 phoneNumber, 복주머니형 pouchIndex). "
                    + "참여가 없으면 404 가 아니라 빈 목록과 0 집계를 준다.")
    @GetMapping
    public ApiResponse<EventParticipantListResponse> getParticipants(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthUser admin,
            @RequestParam(required = false) ParticipationResultStatus resultStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // ★ 상한을 둔다 — size=100000 한 번으로 전화번호 전체를 긁어갈 수 있는 응답이다.
            //   50 은 다른 목록 API 와 같은 값이다 (AdminEventController · PublicEventController).
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ApiResponse.success(
                participantService.getParticipants(eventId, admin, resultStatus, page, size)
        );
    }
}
