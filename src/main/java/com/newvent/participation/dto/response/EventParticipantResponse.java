package com.newvent.participation.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;

import com.newvent.participation.domain.EventParticipation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 관리자가 보는 참여 기록 한 건.
 *
 * ★ {@code MyParticipationResponse} 와 방향이 반대다 — 그쪽은 「내가 어느 이벤트에
 *   참여했나」라서 이벤트 정보를 담고, 이쪽은 「이 이벤트에 누가 참여했나」라서 사람을 담는다.
 *   같은 DTO 로 합치면 절반이 항상 null 이다.
 */
@Builder
public record EventParticipantResponse(

        @Schema(description = "참여 기록 id")
        Long participationId,

        @Schema(description = "참여자 id")
        Long userId,

        @Schema(description = "참여자 이름")
        String userName,

        @Schema(description = "참여 시각")
        OffsetDateTime participatedAt,

        /**
         * 참여할 때 사용자가 낸 값.
         *
         * ★ 방식마다 모양이 다르다 — 기본형은 {@code {}}, 스포츠 예측형은 {@code {"prediction":"2-1"}},
         *   사전예약형은 {@code {"phoneNumber":"01012345678"}}, 복주머니형은 {@code {"pouchIndex":3}}.
         *   그래서 칸을 고정하지 않고 저장된 그대로 준다.
         *
         * ★★ 전화번호가 여기 들어간다. 경품을 보내려면 관리자가 봐야 하는 값이지만,
         *   <b>이 응답은 개인정보다.</b> 이벤트를 만든 관리자만 볼 수 있게 막혀 있고
         *   (EventOwnerCheck), 로그에 본문을 남기지 않는다.
         */
        @Schema(description = "참여 입력. 방식마다 모양이 다르다 (prediction · phoneNumber · pouchIndex). "
                + "기본형은 빈 객체")
        Map<String, Object> submittedData,

        @Schema(description = "결과 상태. WON · LOST · PENDING. 결과가 없는 방식이면 null",
                allowableValues = {"WON", "LOST", "PENDING"})
        String resultStatus,

        @Schema(description = "당첨 경품명. 당첨이 아니면 null")
        String prizeName
) {
    public static EventParticipantResponse from(EventParticipation participation) {
        Map<String, Object> resultData = participation.getResultData();

        return EventParticipantResponse.builder()
                .participationId(participation.getId())
                .userId(participation.getUser().getId())
                .userName(participation.getUser().getName())
                .participatedAt(participation.getCreatedAt())
                .submittedData(participation.getSubmittedData())
                .resultStatus(readString(resultData, "status"))
                .prizeName(readString(resultData, "prizeName"))
                .build();
    }

    private static String readString(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        return value instanceof String text ? text : null;
    }
}
