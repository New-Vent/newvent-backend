package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.EventVersion;

import lombok.Builder;

@Builder
public record EventVersionDetailResponse(
        Long versionId,
        Integer versionNo,
        OffsetDateTime createdAt,
        String htmlContent
) {
    public static EventVersionDetailResponse from(EventVersion version) {

        return EventVersionDetailResponse.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .createdAt(version.getCreatedAt())
                .htmlContent(version.getHtmlContent())
                .build();
    }
}
