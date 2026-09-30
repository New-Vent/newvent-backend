package com.newvent.event.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.newvent.event.service.EventExpirationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventExpirationJob {

    private final EventExpirationService expirationService;

    @Scheduled(
            cron = "${event.expiration.cron:0 * * * * *}",
            zone = "Asia/Seoul"
    )
    public void run() {
        int updated = expirationService.endExpiredEvents();

        if (updated > 0) {
            log.info("기간이 지난 이벤트 {}건 자동 종료", updated);
        }
    }
}
