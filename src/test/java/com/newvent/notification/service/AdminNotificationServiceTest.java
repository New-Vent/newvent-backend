package com.newvent.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.newvent.admin.domain.Admin;
import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.Event;
import com.newvent.notification.domain.AdminNotification;
import com.newvent.notification.domain.AdminNotificationType;
import com.newvent.notification.exception.AdminNotificationErrorCode;
import com.newvent.notification.exception.AdminNotificationException;
import com.newvent.notification.repository.AdminNotificationRepository;

class AdminNotificationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdminNotificationRepository notificationRepository = mock(AdminNotificationRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T01:00:00Z"), SEOUL);
    private final AdminNotificationService service = new AdminNotificationService(notificationRepository, clock);

    @Test
    @DisplayName("전체 목록 조회는 리포지토리 결과를 매핑한다")
    void 목록_조회에_성공한다() {
        AdminNotification notification = notification(1L);
        when(notificationRepository.findByAdminIdAndOptionalEventId(5L, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 10), 1));

        PageResponse<?> result = service.getNotifications(5L, null, 0, 10);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("eventId를 주면 그 이벤트의 알림만 조회한다")
    void 이벤트별_목록_조회에_성공한다() {
        AdminNotification notification = notification(1L);
        when(notificationRepository.findByAdminIdAndOptionalEventId(5L, 10L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 10), 1));

        PageResponse<?> result = service.getNotifications(5L, 10L, 0, 10);

        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("읽음 처리에 성공하면 readAt이 채워진다")
    void 읽음_처리에_성공한다() {
        AdminNotification notification = notification(1L);
        when(notificationRepository.findByIdAndAdminId(1L, 5L)).thenReturn(Optional.of(notification));

        service.markRead(5L, 1L);

        assertThat(notification.read()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않거나 다른 관리자의 알림이면 NOTI404-0이다")
    void 없는_알림_읽음처리는_404를_던진다() {
        when(notificationRepository.findByIdAndAdminId(999L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(5L, 999L))
                .isInstanceOf(AdminNotificationException.class)
                .extracting(ex -> ((AdminNotificationException) ex).getErrorCode().getCode())
                .isEqualTo(AdminNotificationErrorCode.NOTIFICATION_NOT_FOUND.getCode());
    }

    private AdminNotification notification(Long id) {
        Admin admin = BeanUtils.instantiateClass(Admin.class);
        ReflectionTestUtils.setField(admin, "id", 5L);
        Event event = BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "id", 10L);
        ReflectionTestUtils.setField(event, "title", "테스트 이벤트");

        AdminNotification notification = AdminNotification.of(admin, event, AdminNotificationType.STARTED, "메시지");
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }
}
