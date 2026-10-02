package com.newvent.notification.dto.response;

import java.time.OffsetDateTime;

import com.newvent.notification.domain.AdminNotification;

public record AdminNotificationResponse(
        Long id,
        Long eventId,
        String eventName,
        String type,
        String message,
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
