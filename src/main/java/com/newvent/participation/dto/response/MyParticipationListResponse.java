package com.newvent.participation.dto.response;

import com.newvent.common.response.PageResponse;

public record MyParticipationListResponse(
        ParticipationSummaryResponse summary,
        PageResponse<MyParticipationResponse> participations
) {
}
