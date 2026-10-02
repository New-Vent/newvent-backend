package com.newvent.participation.dto.response;

import lombok.Builder;

@Builder
public record ParticipationSummaryResponse(
        long totalParticipationCount,
        long rewardCount,
        long pendingCount
) {
}
