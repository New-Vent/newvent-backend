package com.newvent.event.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record EventVersionListResponse(
        Long eventId,
        String title,
        List<EventVersionSummaryResponse> versions
) {
}
