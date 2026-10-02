package com.newvent.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.newvent.admin.domain.Admin;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.repository.EventRepository;
import com.newvent.notification.domain.AdminNotification;
import com.newvent.notification.domain.AdminNotificationType;
import com.newvent.notification.repository.AdminNotificationRepository;
import com.newvent.user.domain.MembershipGrade;

class EventNotificationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final AdminNotificationRepository notificationRepository = mock(AdminNotificationRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T01:00:00Z"), SEOUL);
    private final EventNotificationService service =
            new EventNotificationService(eventRepository, notificationRepository, clock);

    @Test
    @DisplayName("시작한 이벤트는 소유 관리자에게 STARTED 알림을 남기고 startNotifiedAt을 채운다")
    void 시작_알림에_성공한다() {
        Admin admin = admin(5L);
        Event event = event(1L, admin);
        when(eventRepository.findStartingEvents(eq(EventStatus.PUBLISHED), any()))
                .thenReturn(List.of(event));

        int count = service.notifyStartingEvents();

        assertThat(count).isEqualTo(1);
        assertThat(event.getStartNotifiedAt()).isEqualTo(OffsetDateTime.now(clock));
        verify(notificationRepository).save(argThatTypeIs(AdminNotificationType.STARTED));
    }

    @Test
    @DisplayName("대상이 없으면 아무 알림도 남기지 않는다")
    void 대상_없으면_저장하지_않는다() {
        when(eventRepository.findStartingEvents(eq(EventStatus.PUBLISHED), any())).thenReturn(List.of());

        int count = service.notifyStartingEvents();

        assertThat(count).isZero();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("마감임박 이벤트는 CLOSING_SOON 알림을 남기고 closingSoonNotifiedAt을 채운다")
    void 마감임박_알림에_성공한다() {
        Admin admin = admin(5L);
        Event event = event(2L, admin);
        when(eventRepository.findClosingSoonEvents(eq(EventStatus.PUBLISHED), any(), any()))
                .thenReturn(List.of(event));

        int count = service.notifyClosingSoonEvents();

        assertThat(count).isEqualTo(1);
        assertThat(event.getClosingSoonNotifiedAt()).isEqualTo(OffsetDateTime.now(clock));
        verify(notificationRepository).save(argThatTypeIs(AdminNotificationType.CLOSING_SOON));
    }

    @Test
    @DisplayName("기간이 3일보다 길면 마감임박 알림을 보낸다")
    void 기간이_길면_마감임박을_보낸다() {
        Admin admin = admin(5L);
        Event event = event(2L, admin);
        ReflectionTestUtils.setField(event, "startDate", OffsetDateTime.now(clock).minusDays(10));
        ReflectionTestUtils.setField(event, "endDate", OffsetDateTime.now(clock).plusDays(1));
        when(eventRepository.findClosingSoonEvents(eq(EventStatus.PUBLISHED), any(), any()))
                .thenReturn(List.of(event));

        int count = service.notifyClosingSoonEvents();

        assertThat(count).isEqualTo(1);
        verify(notificationRepository).save(argThatTypeIs(AdminNotificationType.CLOSING_SOON));
    }

    @Test
    @DisplayName("기간이 3일 이하인 이벤트는 시작과 동시에 마감임박이라 CLOSING_SOON을 생략한다")
    void 기간이_짧으면_마감임박을_생략한다() {
        Admin admin = admin(5L);
        Event event = event(4L, admin);
        ReflectionTestUtils.setField(event, "startDate", OffsetDateTime.now(clock));
        ReflectionTestUtils.setField(event, "endDate", OffsetDateTime.now(clock).plusDays(3));
        when(eventRepository.findClosingSoonEvents(eq(EventStatus.PUBLISHED), any(), any()))
                .thenReturn(List.of(event));

        int count = service.notifyClosingSoonEvents();

        assertThat(count).isZero();
        assertThat(event.getClosingSoonNotifiedAt()).isNull();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("종료된 이벤트는 ENDED 알림을 남기고 endNotifiedAt을 채운다")
    void 종료_알림에_성공한다() {
        Admin admin = admin(5L);
        Event event = event(3L, admin);
        when(eventRepository.findEndedEventsNeedingNotification(eq(EventStatus.ENDED)))
                .thenReturn(List.of(event));

        int count = service.notifyEndedEvents();

        assertThat(count).isEqualTo(1);
        assertThat(event.getEndNotifiedAt()).isEqualTo(OffsetDateTime.now(clock));
        verify(notificationRepository).save(argThatTypeIs(AdminNotificationType.ENDED));
    }

    private AdminNotification argThatTypeIs(AdminNotificationType type) {
        return org.mockito.ArgumentMatchers.argThat(
                notification -> notification.getType() == type);
    }

    private Admin admin(Long id) {
        Admin admin = org.springframework.beans.BeanUtils.instantiateClass(Admin.class);
        ReflectionTestUtils.setField(admin, "id", id);
        return admin;
    }

    private Event event(Long id, Admin owner) {
        Event event = org.springframework.beans.BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "id", id);
        ReflectionTestUtils.setField(event, "ownerAdmin", owner);
        ReflectionTestUtils.setField(event, "title", "테스트 이벤트");
        ReflectionTestUtils.setField(event, "status", EventStatus.PUBLISHED);
        ReflectionTestUtils.setField(event, "grade", MembershipGrade.NORMAL);
        return event;
    }
}
