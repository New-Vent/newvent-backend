package com.newvent.participation.dto.response;

import java.util.Map;

import com.newvent.participation.domain.EventGameConfig;

import io.swagger.v3.oas.annotations.media.Schema;

/** 저장된 참여 설정. 보낸 값을 그대로 되돌려준다 — 서버가 손대지 않았음을 확인할 수 있다. */
public record ParticipationConfigResponse(
        @Schema(description = "이벤트 ID") Long eventId,
        @Schema(description = "참여 방식", example = "LUCKY_POUCH") String gameCode,
        @Schema(description = "저장된 설정") Map<String, Object> config) {

    public static ParticipationConfigResponse from(Long eventId, EventGameConfig saved) {
        return new ParticipationConfigResponse(
                eventId, saved.getGame().getCode(), saved.getConfig());
    }
}
