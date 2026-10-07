package com.newvent.participation.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.repository.EventRepository;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventGameConfigRepository;
import com.newvent.participation.repository.EventParticipationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DelayedEventDrawService {

    private final EventRepository eventRepository;
    private final EventGameConfigRepository eventGameConfigRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public List<Long> findCandidateEventIds() {
        return eventParticipationRepository.findDelayedDrawCandidateEventIds(
            List.of(EventStatus.PUBLISHED, EventStatus.ENDED),
            OffsetDateTime.now(clock)
        );
    }

    // Job에서 이벤트별로 호출하므로 이벤트마다 별도의 트랜잭션이 실행된다.
    @Transactional
    public void drawEvent(Long eventId) {
        Event event = eventRepository.findByIdForDraw(eventId)
            .orElse(null);

        if (event == null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);

        // 대상 조회 이후 삭제·상태·기간이 변경됐을 수 있어 다시 확인
        if (event.getDeletedAt() != null
            || (event.getStatus() != EventStatus.PUBLISHED && event.getStatus() != EventStatus.ENDED)
            || event.getEndDate() == null
            || !event.getEndDate().isBefore(now)) {
            return;
        }

        List<EventGameConfig> configs = eventGameConfigRepository.findAllByEventId(eventId);

        if (configs.size() != 1) {
            throw invalidConfig(eventId, "참여 설정은 이벤트당 하나여야 합니다.");
        }

        EventGameConfig gameConfig = configs.getFirst();

        if (gameConfig.getGame() == null || !"BASIC".equals(gameConfig.getGame().getCode())) return;

        Map<String, Object> config = gameConfig.getConfig();

        if (config == null || !"DELAYED".equals(config.get("resultMode"))) return;

        int winnerCount = readWinnerCount(eventId, config.get("winnerCount"));
        OffsetDateTime announcementAt = readAnnouncementAt(eventId, config.get("announcementAt"));
        String prizeName = readPrizeName(eventId, config.get("prizeName"));

        if (!announcementAt.isAfter(event.getEndDate())) {
            throw invalidConfig(eventId, "발표 시각은 이벤트 종료 이후여야 합니다.");
        }

        if (announcementAt.isAfter(now)) return;

        // 잠금을 기다린 실행도 여기서 최신 PENDING 목록을 다시 조회한다.
        List<EventParticipation> pending = new ArrayList<>(
            eventParticipationRepository.findPendingByEventId(eventId)
        );

        if (pending.isEmpty()) return;

        Collections.shuffle(pending, random);

        int actualWinnerCount = Math.min(winnerCount, pending.size());

        for (int index = 0; index < pending.size(); index++) {
            Map<String, Object> result = index < actualWinnerCount
                ? Map.of("status", "WON", "prizeName", prizeName)
                : Map.of("status", "LOST");

            pending.get(index).updateResultData(result);
        }

        // 조회한 엔티티이므로 트랜잭션 커밋 시 변경 감지로 저장
        log.info(
            "추후 추첨 결과 변경: eventId={}, participantCount={}, winnerCount={}",
            eventId,
            pending.size(),
            actualWinnerCount
        );
    }

    private int readWinnerCount(Long eventId, Object value) {
        if (!(value instanceof Number number)) {
            throw invalidConfig(eventId, "당첨 인원은 정수여야 합니다.");
        }

        int winnerCount;

        try {
            winnerCount = new BigDecimal(number.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw invalidConfig(eventId, "당첨 인원은 정수 범위 내 값이어야 합니다.");
        }

        if (winnerCount < 1) {
            throw invalidConfig(eventId, "당첨 인원은 1 이상이어야 합니다.");
        }

        return winnerCount;
    }

    private OffsetDateTime readAnnouncementAt(Long eventId, Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidConfig(eventId, "발표 시각이 필요합니다.");
        }

        try {
            return OffsetDateTime.parse(text);
        } catch (java.time.format.DateTimeParseException exception) {
            throw invalidConfig(eventId, "발표 시각 형식이 올바르지 않습니다.");
        }
    }

    private String readPrizeName(Long eventId, Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidConfig(eventId, "경품명이 필요합니다.");
        }

        return text.trim();
    }

    private ParticipationException invalidConfig(Long eventId, String message) {
        log.error(
            "추후 추첨 설정 오류: eventId={}, reason={}",
            eventId,
            message
        );

        return new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED);
    }
}
