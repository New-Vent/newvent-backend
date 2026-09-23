package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventVersion;
import com.newvent.generation.domain.ChatMessage;

import lombok.Builder;

@Builder
public record EventVersionSummaryResponse(
        Long versionId,
        Integer versionNo,
        OffsetDateTime createdAt,
        boolean published,
        Integer sourceVersionNo,
        String requestContent
) {
    public static EventVersionSummaryResponse from(EventVersion version, Event event) {
        ChatMessage message = version.getRequestMessage();

        boolean published = event.getStatus() == EventStatus.PUBLISHED
                && event.getPublishedVersion() != null
                && version.getId().equals(event.getPublishedVersion().getId());

        return EventVersionSummaryResponse.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .createdAt(version.getCreatedAt())
                .published(published)
                .sourceVersionNo(version.getSourceVersion() == null
                        ? null : version.getSourceVersion().getVersionNo())

                // TODO: 수정 내용 요약 방식을 결정하면 별도 필드로 제공한다.
                .requestContent(message == null ? null : message.getContent())
                .build();
    }
}
