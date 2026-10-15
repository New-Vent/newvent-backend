package com.newvent.notification.dto.response;

import java.time.OffsetDateTime;

import com.newvent.notification.domain.AdminNotification;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminNotificationResponse(
        Long id,
        Long eventId,
        @Schema(description = "이벤트의 현재 제목")
        String eventName,
        @Schema(description = "알림 종류", allowableValues = {"STARTED", "CLOSING_SOON", "ENDED"})
        String type,
        String message,
        @Schema(description = "읽음 처리했는지")
        boolean read,
        OffsetDateTime createdAt
) {
    public static AdminNotificationResponse from(AdminNotification notification) {
        return new AdminNotificationResponse(
                notification.getId(),
                notification.getEvent().getId(),
                notification.getEvent().getTitle(),
                notification.getType().name(),
                notification.getMessage(),
                notification.read(),
                notification.getCreatedAt());
    }
}
