package com.newvent.participation.dto.response;

public record ParticipationSummaryResponse(
        long totalParticipationCount,
        long rewardCount,
        long pendingCount
) {
}
