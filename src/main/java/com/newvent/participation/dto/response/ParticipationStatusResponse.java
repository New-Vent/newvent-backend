package com.newvent.participation.dto.response;

import com.newvent.participation.domain.ParticipationUnavailableReason;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;

@Builder
public record ParticipationStatusResponse(
        @Schema(description = "이 이벤트에 이미 참여했나")
        boolean participated,

        @Schema(description = "지금 참여할 수 있나. false 면 unavailableReason 에 이유가 있다")
        boolean canParticipate,

        @Schema(description = "참여할 수 없는 이유. 참여 가능하면 null")
        ParticipationUnavailableReason unavailableReason,

        /**
         * 참여 방식.
         *
         * ★ 이게 없으면 화면이 어떤 입력을 받아야 할지 모른다.
         *   ParticipationCreateRequest 는 prediction · phoneNumber · pouchIndex 셋을 받는데,
         *   방식마다 **하나만** 보내야 하고 나머지를 보내면 400 이다
         *   (ParticipationValidator 가 방식별로 거른다).
         *
         * ★ 설정이 없으면 null 이다. 그때는 unavailableReason 이 NOT_CONFIGURED 다.
         */
        @Schema(
                description = "참여 방식. BASIC 은 추가 입력 없음, SPORTS_PREDICTION 은 prediction, "
                        + "PRE_REGISTRATION 은 phoneNumber, LUCKY_POUCH 는 pouchIndex 를 보낸다. "
                        + "참여 설정이 없으면 null",
                allowableValues = {"BASIC", "SPORTS_PREDICTION", "PRE_REGISTRATION", "LUCKY_POUCH"},
                example = "BASIC")
        String gameCode
) {
}
