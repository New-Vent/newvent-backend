package com.newvent.participation.dto.response;

import com.newvent.participation.domain.ParticipationUnavailableReason;

import lombok.Builder;

@Builder
public record ParticipationStatusResponse(
        boolean participated,
        boolean canParticipate,
        ParticipationUnavailableReason unavailableReason
) {
}
