package com.newvent.notification.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.service.EventService;
import com.newvent.notification.domain.AdminNotification;
import com.newvent.notification.domain.AdminNotificationType;
import com.newvent.notification.repository.AdminNotificationRepository;

import lombok.RequiredArgsConstructor;

// 이벤트 시작·마감임박·종료를 감지해 그 이벤트의 소유 관리자에게 알림을 남긴다.
@Service
@RequiredArgsConstructor
public class EventNotificationService {

    private final EventRepository eventRepository;
    private final AdminNotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional
    public int notifyStartingEvents() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Event> due = eventRepository.findStartingEvents(EventStatus.PUBLISHED, now);

        for (Event event : due) {
            save(event, AdminNotificationType.STARTED, "'" + event.getTitle() + "' 이벤트가 시작됐습니다.");
            event.markStartNotified(now);
        }
        return due.size();
    }

    @Transactional
    public int notifyClosingSoonEvents() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime threshold = now.plus(EventService.CLOSING_SOON_WINDOW);
        List<Event> due = eventRepository.findClosingSoonEvents(EventStatus.PUBLISHED, now, threshold).stream()
                .filter(this::hasDistinctClosingSoonPhase)
                .toList();

        for (Event event : due) {
            save(event, AdminNotificationType.CLOSING_SOON, "'" + event.getTitle() + "' 이벤트 종료가 얼마 남지 않았습니다.");
            event.markClosingSoonNotified(now);
        }
        return due.size();
    }

    // 전체 기간이 마감임박 기준(3일)보다 짧거나 같으면 시작하자마자 이미 마감임박이라
    // STARTED 와 겹쳐서 뜬다 — 별도로 알릴 구간 자체가 없으므로 생략한다.
    private boolean hasDistinctClosingSoonPhase(Event event) {
        return event.getStartDate() == null
                || event.getStartDate().plus(EventService.CLOSING_SOON_WINDOW).isBefore(event.getEndDate());
    }

    @Transactional
    public int notifyEndedEvents() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Event> due = eventRepository.findEndedEventsNeedingNotification(EventStatus.ENDED);

        for (Event event : due) {
            save(event, AdminNotificationType.ENDED, "'" + event.getTitle() + "' 이벤트가 종료됐습니다.");
            event.markEndNotified(now);
        }
        return due.size();
    }

    private void save(Event event, AdminNotificationType type, String message) {
        notificationRepository.save(AdminNotification.of(event.getOwnerAdmin(), event, type, message));
    }
}
