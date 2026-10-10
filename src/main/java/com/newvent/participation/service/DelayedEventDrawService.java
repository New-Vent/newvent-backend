package com.newvent.participation.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.repository.EventRepository;
import com.newvent.participation.domain.EventGameConfig;
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

    // 한 번에 조회·갱신할 개수
    private static final int DRAW_BATCH_SIZE = 1_000;

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

        int remainingWinnerCount = winnerCount;
        long wonCount = 0;

        // 설정된 당첨 인원까지 최대 1,000명씩 선정·갱신한다.
        while (remainingWinnerCount > 0) {
            int batchSize = Math.min(DRAW_BATCH_SIZE, remainingWinnerCount);

            List<Long> winnerIds = eventParticipationRepository.findRandomPendingIds(eventId, batchSize);

            // 남은 대기 참여자가 없으면 당첨자 선정을 종료한다.
            if (winnerIds.isEmpty()) break;

            int updatedCount = eventParticipationRepository.updateWinners(
                eventId,
                winnerIds,
                prizeName
            );

            // 갱신 건수가 다르면 앞서 처리한 묶음까지 전체 롤백한다.
            if (updatedCount != winnerIds.size()) {
                throw new IllegalStateException("추후 추첨 당첨 결과 변경 건수가 일치하지 않습니다. eventId=" + eventId);
            }

            wonCount += updatedCount;
            remainingWinnerCount -= updatedCount;
        }

        // 당첨 처리 후 남은 대기 참여자도 최대 1,000명씩 갱신한다.
        long lostCount = 0;

        while (true) {
            int updatedCount = eventParticipationRepository.updatePendingAsLostInBatch(eventId, DRAW_BATCH_SIZE);

            if (updatedCount == 0) break;

            lostCount += updatedCount;
        }

        if (wonCount + lostCount == 0) return;

        log.info(
            "추후 추첨 결과 변경: eventId={}, participantCount={}, winnerCount={}",
            eventId,
            wonCount + lostCount,
            wonCount
        );
    }

    private int readWinnerCount(Long eventId, Object value) {
        return ParticipationConfigReader.readInteger(
            value,
            1,
            Integer.MAX_VALUE,
            () -> invalidConfig(
                eventId,
                "당첨 인원은 1 이상의 정수 범위 내 값이어야 합니다."
            )
        );
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
        return ParticipationConfigReader.readPrizeName(
            value,
            () -> invalidConfig(eventId, "경품명이 필요합니다.")
        );
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
