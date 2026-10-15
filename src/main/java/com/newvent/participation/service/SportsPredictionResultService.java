package com.newvent.participation.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.auth.dto.AuthUser;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.participation.config.SportsPredictionConfig;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventGameConfigRepository;
import com.newvent.participation.repository.EventParticipationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 스포츠 예측형 이벤트의 경기 결과 등록과 당첨 결과 확정을 담당한다.
 * 발표 시각 이후 혜택별 결과를 처리하고, 완료된 이벤트의 재추첨을 방지한다.
 */
@Service
@RequiredArgsConstructor
public class SportsPredictionResultService {

    private final EventOwnerCheck ownerCheck;
    private final EventRepository eventRepository;
    private final EventGameConfigRepository configRepository;
    private final EventParticipationRepository participationRepository;
    private final Clock clock;

    private static final int DRAW_BATCH_SIZE = 1_000;

    /**
     * 이벤트를 만든 관리자가 실제 경기 결과를 등록한다.
     * 발표 처리 완료 전까지 결과 정정이 가능하다.
     */
    @Transactional
    public void registerResult(Long eventId, AuthUser admin, String winningPrediction) {
        ownerCheck.requireOwned(eventId, admin);

        Event event = eventRepository.findByIdForDraw(eventId)
            .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));

        if (event.deleted()) {
            throw new EventException(EventErrorCode.EVENT_NOT_FOUND);
        }

        EventGameConfig gameConfig = requireSportsConfig(eventId);
        Map<String, Object> config = gameConfig.getConfig();

        // drawnAt은 config JSON에 저장하는 추첨 완료 시각
        // 추첨 완료 후에는 확정된 당첨 결과가 바뀌지 않도록 정답 수정을 막는다.
        if (config.containsKey("drawnAt")) {
            throw new ParticipationException(ParticipationErrorCode.SPORTS_RESULT_ALREADY_FINALIZED);
        }

        SportsPredictionConfig sports = SportsPredictionConfig.read(config);
        requireAnnouncementAfterEnd(event, sports);

        if (!sports.predictionOptions().contains(winningPrediction)) {
            throw new ParticipationException(ParticipationErrorCode.INVALID_SPORTS_RESULT);
        }

        Map<String, Object> updated = new LinkedHashMap<>(config);
        updated.put("winningPrediction", winningPrediction);

        gameConfig.changeConfig(gameConfig.getGame(), updated);
    }

    @Transactional(readOnly = true)
    public List<Long> findCandidateEventIds() {
        return participationRepository.findSportsDrawCandidateEventIds(OffsetDateTime.now(clock));
    }

    /**
     * Job에서 이벤트별로 호출한다.
     * 모든 혜택 처리와 발표 완료 표시를 하나의 트랜잭션으로 저장한다.
     */
    @Transactional
    public void drawEvent(Long eventId) {
        Event event = eventRepository.findByIdForDraw(eventId).orElse(null);
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (event == null || !ready(event, now)) {
            return;
        }

        EventGameConfig gameConfig = requireSportsConfig(eventId);
        Map<String, Object> config = gameConfig.getConfig();

        if (config.containsKey("drawnAt")) {
            return;
        }

        SportsPredictionConfig sports = SportsPredictionConfig.read(config);
        requireAnnouncementAfterEnd(event, sports);

        if (sports.announcementAt().isAfter(now)) {
            return;
        }

        Object value = config.get("winningPrediction");

        if (value == null) {
            return;
        }

        if (!(value instanceof String winningPrediction) || !sports.predictionOptions().contains(winningPrediction)) {
            throw invalidConfig();
        }

        for (SportsPredictionConfig.Reward reward : sports.rewards()) {
            processRewardInBatches(eventId, winningPrediction, reward);
        }

        // 혜택별 처리가 모두 끝난 뒤 최상위 상태를 확정한다.
        while (true) {
            int updated = participationRepository.finalizeSportsResultsInBatch(eventId, DRAW_BATCH_SIZE);

            if (updated == 0) {
                break;
            }
        }

        // 참가자가 없어도 발표 완료 표시를 저장한다.
        Map<String, Object> updatedConfig = new LinkedHashMap<>(config);
        updatedConfig.put("drawnAt", now.toString());

        gameConfig.changeConfig(gameConfig.getGame(), updatedConfig);
    }

    private EventGameConfig requireSportsConfig(Long eventId) {
        List<EventGameConfig> configs = configRepository.findAllByEventId(eventId);

        if (configs.size() != 1) {
            throw invalidConfig();
        }

        EventGameConfig config = configs.getFirst();

        if (config.getGame() == null || !"SPORTS_PREDICTION".equals(config.getGame().getCode())) {
            throw invalidConfig();
        }

        return config;
    }

    private boolean ready(Event event, OffsetDateTime now) {
        return !event.deleted()
            && (event.getStatus() == EventStatus.PUBLISHED
            || event.getStatus() == EventStatus.ENDED)
            && event.getEndDate() != null
            && event.getEndDate().isBefore(now);
    }

    private void requireAnnouncementAfterEnd(Event event, SportsPredictionConfig sports) {
        if (event.getEndDate() == null || !sports.announcementAt().isAfter(event.getEndDate())) {
            throw invalidConfig();
        }
    }

    private ParticipationException invalidConfig() {
        return new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED);
    }

    private void processRewardInBatches(
        Long eventId,
        String winningPrediction,
        SportsPredictionConfig.Reward reward
    ) {
        if ("DRAW".equals(reward.selection())) {
            int remainingWinnerCount = reward.winnerCount();

            while (remainingWinnerCount > 0) {
                int batchSize = Math.min(
                    DRAW_BATCH_SIZE,
                    remainingWinnerCount
                );

                // 아직 이 혜택을 처리하지 않은 대상자 중 무작위로 선정한다.
                int updated = participationRepository.appendSportsRewardInBatch(
                    eventId,
                    winningPrediction,
                    reward.rewardId(),
                    reward.target(),
                    reward.selection(),
                    reward.prizeName(),
                    true,
                    batchSize
                );

                if (updated == 0) {
                    break;
                }

                remainingWinnerCount -= updated;
            }
        }

        // ALL이면 대상자는 WON, 비대상자는 LOST.
        // DRAW이면 앞에서 선정되지 않은 참여자는 LOST.
        while (true) {
            int updated = participationRepository.appendSportsRewardInBatch(
                eventId,
                winningPrediction,
                reward.rewardId(),
                reward.target(),
                reward.selection(),
                reward.prizeName(),
                false,
                DRAW_BATCH_SIZE
            );

            if (updated == 0) {
                break;
            }
        }
    }
}
