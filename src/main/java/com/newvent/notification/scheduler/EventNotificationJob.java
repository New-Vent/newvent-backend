package com.newvent.notification.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.newvent.notification.service.EventNotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventNotificationJob {

    private final EventNotificationService notificationService;

    @Scheduled(
            cron = "${event.notification.cron:0 * * * * *}",
            zone = "Asia/Seoul"
    )
    public void run() {
        int started = notificationService.notifyStartingEvents();
        int closingSoon = notificationService.notifyClosingSoonEvents();
        int ended = notificationService.notifyEndedEvents();

        if (started > 0 || closingSoon > 0 || ended > 0) {
            log.info("이벤트 알림 발송 — 시작 {}건, 마감임박 {}건, 종료 {}건", started, closingSoon, ended);
        }
    }
}
