package com.newvent.participation.dto.response;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.newvent.participation.domain.EventParticipation;

import lombok.Builder;

@Builder
public record MyParticipationDetailResponse(
        Long participationId,
        Long eventId,
        String eventTitle,
        OffsetDateTime participatedAt,
        Map<String, Object> submittedData,
        Map<String, Object> resultData
) {
    public static MyParticipationDetailResponse from(EventParticipation participation) {
        return MyParticipationDetailResponse.builder()
                .participationId(participation.getId())
                .eventId(participation.getEvent().getId())
                .eventTitle(participation.getEvent().getTitle())
                .participatedAt(participation.getCreatedAt())
                .submittedData(new LinkedHashMap<>(participation.getSubmittedData()))
                .resultData(new LinkedHashMap<>(participation.getResultData()))
                .build();
    }
}
