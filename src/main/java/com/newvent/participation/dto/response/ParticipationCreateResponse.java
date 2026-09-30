package com.newvent.participation.dto.response;

import lombok.Builder;

@Builder
public record ParticipationCreateResponse(
        Long participationId,
        Long eventId
) {}
