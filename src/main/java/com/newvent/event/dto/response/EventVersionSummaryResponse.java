package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventVersion;
import com.newvent.generation.domain.ChatMessage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record EventVersionSummaryResponse(
        Long versionId,
        Integer versionNo,
        OffsetDateTime createdAt,
        @Schema(description = "저장 지점이면 true. 저장 지점 목록은 이 값으로 거른다.")
        boolean checkpoint,
        @Schema(description = "이벤트가 게시 중이고 이 버전이 게시 버전이면 true")
        boolean published,
        @Schema(description = "이어서 작업한 원본 버전 번호. 처음 만든 버전이면 null")
        Integer sourceVersionNo,
        @Schema(description = "이 버전을 만든 수정 요청 원문(요약 아님). 연결된 요청이 없으면 null")
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
                .checkpoint(version.isCheckpoint())
                .published(published)
                .sourceVersionNo(version.getSourceVersion() == null
                        ? null : version.getSourceVersion().getVersionNo())

                // TODO: 수정 내용 요약 방식을 결정하면 별도 필드로 제공한다.
                .requestContent(message == null ? null : message.getContent())
                .build();
    }
}
