package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventVersion;

import lombok.Builder;

@Builder
public record EventVersionSummaryResponse(
        Long versionId,
        Integer versionNo,
        OffsetDateTime createdAt,
        boolean checkpoint,
        boolean published,
        Integer sourceVersionNo,
        String changeSummary
) {
    public static EventVersionSummaryResponse from(EventVersion version, Event event) {
        boolean published = event.getStatus() == EventStatus.PUBLISHED
                && event.getPublishedVersion() != null
                && version.getId().equals(event.getPublishedVersion().getId());

        return EventVersionSummaryResponse.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .createdAt(version.getCreatedAt())
                .checkpoint(version.isCheckpoint())
                .published(published)
                .sourceVersionNo(version.getSourceVersion() == null
                        ? null : version.getSourceVersion().getVersionNo())
                .changeSummary(version.getChangeSummary())
                .build();
    }
}
