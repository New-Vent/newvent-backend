package com.newvent.participation.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;

import com.newvent.participation.domain.EventParticipation;

import lombok.Builder;

@Builder
public record MyParticipationResponse(
        Long participationId,
        Long eventId,
        String eventTitle,
        OffsetDateTime participatedAt,
        String resultStatus,
        String prizeName
) {
    public static MyParticipationResponse from(EventParticipation participation) {
        Map<String, Object> resultData = participation.getResultData();

        return MyParticipationResponse.builder()
                .participationId(participation.getId())
                .eventId(participation.getEvent().getId())
                .eventTitle(participation.getEvent().getTitle())
                .participatedAt(participation.getCreatedAt())
                .resultStatus(readString(resultData, "status"))
                .prizeName(readString(resultData, "prizeName"))
                .build();
    }

    private static String readString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof String text ? text : null;
    }
}
