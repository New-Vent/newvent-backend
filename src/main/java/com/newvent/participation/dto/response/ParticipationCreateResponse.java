package com.newvent.participation.dto.response;

import java.util.Map;

import lombok.Builder;

@Builder
public record ParticipationCreateResponse(
        Long participationId,
        Long eventId,
        Map<String, Object> resultData
) {}
