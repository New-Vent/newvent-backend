package com.newvent.participation.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 참여 설정 저장 요청.
 *
 * ★ config 의 모양은 gameCode 마다 다르다. 그래서 JSON 을 그대로 받고
 *   ParticipationValidator.validateConfig 가 방식별로 검사한다 —
 *   DTO 로 방식마다 타입을 만들면 방식을 추가할 때마다 DTO 가 늘어난다.
 */
public record ParticipationConfigRequest(
        @Schema(
                description = "참여 방식. games.code 와 같아야 한다",
                allowableValues = {"BASIC", "SPORTS_PREDICTION", "PRE_REGISTRATION", "LUCKY_POUCH"},
                example = "LUCKY_POUCH")
        @NotBlank
        String gameCode,

        @Schema(
                description = "방식별 설정. BASIC 은 resultMode(DELAYED·IMMEDIATE·GUARANTEED)와 "
                        + "그에 필요한 winProbability(0~100)·prizeName. "
                        + "SPORTS_PREDICTION 은 predictionOptions 배열, announcementAt(종료 이후 발표 시각), "
                        + "rewards 배열이 필요하다. 각 혜택은 rewardId, target(ALL_PARTICIPANTS·CORRECT_PREDICTIONS), "
                        + "selection(ALL·DRAW), prizeName을 포함하며, DRAW는 winnerCount도 필요하다. "
                        + "PRE_REGISTRATION 은 설정이 없다(빈 객체). "
                        + "LUCKY_POUCH 는 pouches 배열 — 번호는 1부터 길이까지 빠짐없이, "
                        + "칸마다 winProbability 와 prizeName 이 모두 필요하다. "
                        + "모양이 맞지 않으면 409 (PARTICIPATION409-1)",
                example = "{\"pouches\":[{\"pouchIndex\":1,\"winProbability\":10,\"prizeName\":\"상품권\"},"
                        + "{\"pouchIndex\":2,\"winProbability\":100,\"prizeName\":\"데이터 1GB\"}]}")
        Map<String, Object> config) {

    /** 설정을 안 보내면 빈 객체로 본다 — PRE_REGISTRATION 은 설정이 없다. */
    public Map<String, Object> configOrEmpty() {
        return config == null ? Map.of() : config;
    }
}
