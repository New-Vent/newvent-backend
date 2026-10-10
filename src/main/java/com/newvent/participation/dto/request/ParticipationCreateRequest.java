package com.newvent.participation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

// 사용자가 제출할 값만 받음
public record ParticipationCreateRequest(
        @Schema(description = "스포츠 예측형에서 고른 선택지. 참여 설정의 predictionOptions 중 하나. 다른 참여 방식에서는 보내지 않는다.")
        String prediction,
        @Schema(description = "사전예약형에서 받을 전화번호. 다른 참여 방식에서는 보내지 않는다.")
        String phoneNumber,
        @Schema(description = "복주머니형에서 고른 주머니 번호 (1부터 주머니 수까지). 다른 참여 방식에서는 보내지 않는다.")
        Integer pouchIndex
) {
}
