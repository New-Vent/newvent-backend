package com.newvent.participation.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.newvent.participation.service.SportsPredictionResultService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SportsPredictionDrawJob {

    private final SportsPredictionResultService resultService;

    @Scheduled(
        cron = "${event.sports-draw.cron:0 * * * * *}",
        zone = "Asia/Seoul"
    )
    public void drawSportsEvents() {
        for (Long eventId : resultService.findCandidateEventIds()) {
            try {
                resultService.drawEvent(eventId);
            } catch (Exception exception) {
                log.error(
                    "스포츠 예측 발표 처리 실패: eventId={}",
                    eventId,
                    exception
                );
            }
        }
    }
}
