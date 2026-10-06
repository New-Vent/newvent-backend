package com.newvent.notification.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.common.response.PageResponse;
import com.newvent.notification.domain.AdminNotification;
import com.newvent.notification.dto.response.AdminNotificationResponse;
import com.newvent.notification.exception.AdminNotificationErrorCode;
import com.newvent.notification.exception.AdminNotificationException;
import com.newvent.notification.repository.AdminNotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private final AdminNotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminNotificationResponse> getNotifications(Long adminId, Long eventId, int page, int size) {
        Page<AdminNotification> result = notificationRepository
                .findByAdminIdAndOptionalEventId(adminId, eventId, PageRequest.of(page, size));

        List<AdminNotificationResponse> content = result.getContent().stream()
                .map(AdminNotificationResponse::from)
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    @Transactional
    public void markRead(Long adminId, Long notificationId) {
        AdminNotification notification = notificationRepository.findByIdAndAdminId(notificationId, adminId)
                .orElseThrow(() -> new AdminNotificationException(AdminNotificationErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(OffsetDateTime.now(clock));
    }
}
