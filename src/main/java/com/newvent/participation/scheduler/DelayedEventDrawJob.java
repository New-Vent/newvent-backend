package com.newvent.participation.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.newvent.participation.service.DelayedEventDrawService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DelayedEventDrawJob {

    private final DelayedEventDrawService delayedEventDrawService;

    @Scheduled(
        cron = "${event.draw.cron:0 * * * * *}",
        zone = "Asia/Seoul"
    )
    public void drawDelayedEvents() {
        for (Long eventId : delayedEventDrawService.findCandidateEventIds()) {
            try {
                delayedEventDrawService.drawEvent(eventId);
            } catch (Exception exception) {
                log.error(
                    "추후 추첨 처리 실패: eventId={}",
                    eventId,
                    exception
                );
            }
        }
    }
}
