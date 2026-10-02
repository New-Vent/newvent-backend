package com.newvent.participation.dto.response;

import com.newvent.event.dto.response.PageResponse;

public record MyParticipationListResponse(
        ParticipationSummaryResponse summary,
        PageResponse<MyParticipationResponse> participations
) {
}
