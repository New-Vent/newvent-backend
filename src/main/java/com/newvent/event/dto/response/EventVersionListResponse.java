package com.newvent.event.dto.response;

import java.util.List;

import lombok.Builder;

@Builder
public record EventVersionListResponse(
        Long eventId,
        String title,
        List<EventVersionSummaryResponse> versions
) {
}
