package com.newvent.participation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.newvent.auth.dto.AuthUser;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.Game;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventGameConfigRepository;
import com.newvent.participation.repository.EventParticipationRepository;

class SportsPredictionResultServiceTest {

    private static final Long EVENT_ID = 1L;
    private static final AuthUser ADMIN = AuthUser.admin(3L);

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-15T14:00:00+09:00");

    private final EventOwnerCheck ownerCheck = mock(EventOwnerCheck.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final EventGameConfigRepository configRepository = mock(EventGameConfigRepository.class);
    private final EventParticipationRepository participationRepository = mock(EventParticipationRepository.class);

    private SportsPredictionResultService service;
    private Event event;
    private EventGameConfig gameConfig;

    @BeforeEach
    void setUp() {
        service = new SportsPredictionResultService(
            ownerCheck,
            eventRepository,
            configRepository,
            participationRepository,
            Clock.fixed(NOW.toInstant(), NOW.getOffset())
        );

        event = mock(Event.class);
        when(event.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(event.getEndDate()).thenReturn(NOW.minusDays(1));

        when(eventRepository.findByIdForDraw(EVENT_ID))
            .thenReturn(Optional.of(event));

        Game game = mock(Game.class);
        when(game.getCode()).thenReturn("SPORTS_PREDICTION");

        // 실제 엔티티를 사용해서 config 변경 결과를 확인한다.
        gameConfig = EventGameConfig.create(event, game, config());

        when(configRepository.findAllByEventId(EVENT_ID))
            .thenReturn(List.of(gameConfig));
    }

    @Test
    void 종료_전에도_정답을_등록하고_기존_설정은_보존한다() {
        when(event.getEndDate()).thenReturn(NOW.plusHours(1));

        Map<String, Object> updated = new LinkedHashMap<>(gameConfig.getConfig());
        updated.put("announcementAt", NOW.plusHours(2).toString());
        gameConfig.changeConfig(gameConfig.getGame(), updated);

        service.registerResult(EVENT_ID, ADMIN, "KOREA_WIN");

        assertThat(gameConfig.getConfig())
            .containsEntry("winningPrediction", "KOREA_WIN")
            .containsEntry(
                "predictionOptions",
                List.of("KOREA_WIN", "DRAW", "JAPAN_WIN")
            )
            .containsKey("rewards")
            .doesNotContainKey("drawnAt");

        verifyNoInteractions(participationRepository);
    }

    @Test
    void 발표_완료_전에는_정답을_정정할_수_있다() {
        service.registerResult(EVENT_ID, ADMIN, "KOREA_WIN");
        service.registerResult(EVENT_ID, ADMIN, "DRAW");

        assertThat(gameConfig.getConfig())
            .containsEntry("winningPrediction", "DRAW");
    }

    @Test
    void 선택지에_없는_정답은_거부한다() {
        assertError(
            () -> service.registerResult(EVENT_ID, ADMIN, "UNKNOWN"),
            ParticipationErrorCode.INVALID_SPORTS_RESULT
        );

        assertThat(gameConfig.getConfig())
            .doesNotContainKey("winningPrediction");
    }

    @Test
    void 발표_완료_후에는_정답을_수정할_수_없다() {
        putConfig("winningPrediction", "KOREA_WIN");
        putConfig("drawnAt", NOW.toString());

        assertError(
            () -> service.registerResult(EVENT_ID, ADMIN, "DRAW"),
            ParticipationErrorCode.SPORTS_RESULT_ALREADY_FINALIZED
        );

        assertThat(gameConfig.getConfig())
            .containsEntry("winningPrediction", "KOREA_WIN");
    }

    @Test
    void 남의_이벤트는_정답을_등록할_수_없다() {
        when(ownerCheck.requireOwned(EVENT_ID, ADMIN))
            .thenThrow(new AccessDeniedException("접근 권한 없음"));

        assertThatThrownBy(
            () -> service.registerResult(EVENT_ID, ADMIN, "KOREA_WIN")
        ).isInstanceOf(AccessDeniedException.class);

        assertThat(gameConfig.getConfig())
            .doesNotContainKey("winningPrediction");
    }

    @Test
    void 발표_전에는_결과를_처리하지_않는다() {
        putConfig("winningPrediction", "KOREA_WIN");
        putConfig("announcementAt", NOW.plusSeconds(1).toString());

        service.drawEvent(EVENT_ID);

        verifyNoInteractions(participationRepository);
        assertThat(gameConfig.getConfig()).doesNotContainKey("drawnAt");
    }

    @Test
    void 정답이_등록되지_않았으면_처리하지_않는다() {
        service.drawEvent(EVENT_ID);

        verifyNoInteractions(participationRepository);
        assertThat(gameConfig.getConfig()).doesNotContainKey("drawnAt");
    }

    @Test
    void 종료_시각과_같으면_아직_처리하지_않는다() {
        when(event.getEndDate()).thenReturn(NOW);
        putConfig("winningPrediction", "KOREA_WIN");

        service.drawEvent(EVENT_ID);

        verifyNoInteractions(participationRepository);
    }

    @Test
    void 발표_시각과_같으면_혜택을_처리하고_완료_시각을_저장한다() {
        putConfig("winningPrediction", "KOREA_WIN");

        when(participationRepository.appendSportsRewardInBatch(
            EVENT_ID,
            "KOREA_WIN",
            "participation",
            "ALL_PARTICIPANTS",
            "ALL",
            "응원 5,000P",
            false,
            1_000
        )).thenReturn(5, 0);

        // 정답자 5명 선정 후 남은 대상자가 없어 종료한다.
        when(participationRepository.appendSportsRewardInBatch(
            EVENT_ID,
            "KOREA_WIN",
            "prediction",
            "CORRECT_PREDICTIONS",
            "DRAW",
            "치킨 쿠폰",
            true,
            30
        )).thenReturn(5);

        when(participationRepository.finalizeSportsResultsInBatch(
            EVENT_ID,
            1_000
        )).thenReturn(5, 0);

        service.drawEvent(EVENT_ID);

        verify(participationRepository, times(2))
            .appendSportsRewardInBatch(
                EVENT_ID,
                "KOREA_WIN",
                "participation",
                "ALL_PARTICIPANTS",
                "ALL",
                "응원 5,000P",
                false,
                1_000
            );

        verify(participationRepository).appendSportsRewardInBatch(
            EVENT_ID,
            "KOREA_WIN",
            "prediction",
            "CORRECT_PREDICTIONS",
            "DRAW",
            "치킨 쿠폰",
            true,
            30
        );

        // 5명을 선정했으므로 남은 당첨 인원은 25명.
        // mock 기본 반환값 0으로 대상자가 없음을 표현한다.
        verify(participationRepository).appendSportsRewardInBatch(
            EVENT_ID,
            "KOREA_WIN",
            "prediction",
            "CORRECT_PREDICTIONS",
            "DRAW",
            "치킨 쿠폰",
            true,
            25
        );

        verify(participationRepository).appendSportsRewardInBatch(
            EVENT_ID,
            "KOREA_WIN",
            "prediction",
            "CORRECT_PREDICTIONS",
            "DRAW",
            "치킨 쿠폰",
            false,
            1_000
        );

        verify(participationRepository, times(2))
            .finalizeSportsResultsInBatch(EVENT_ID, 1_000);

        assertThat(gameConfig.getConfig())
            .containsEntry("drawnAt", NOW.toString());
    }

    @Test
    void 발표가_완료된_이벤트는_재처리하지_않는다() {
        putConfig("winningPrediction", "KOREA_WIN");
        putConfig("drawnAt", NOW.minusMinutes(1).toString());

        service.drawEvent(EVENT_ID);

        verifyNoInteractions(participationRepository);
    }

    @Test
    void 참가자가_없어도_완료_시각을_저장한다() {
        putConfig("winningPrediction", "KOREA_WIN");

        service.drawEvent(EVENT_ID);

        assertThat(gameConfig.getConfig()).containsKey("drawnAt");
    }

    @Test
    void 혜택_처리에서_예외가_발생하면_완료_표시를_남기지_않는다() {
        putConfig("winningPrediction", "KOREA_WIN");

        when(participationRepository.appendSportsRewardInBatch(
            EVENT_ID,
            "KOREA_WIN",
            "participation",
            "ALL_PARTICIPANTS",
            "ALL",
            "응원 5,000P",
            false,
            1_000
        )).thenThrow(new IllegalStateException("강제 실패"));

        assertThatThrownBy(() -> service.drawEvent(EVENT_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("강제 실패");

        assertThat(gameConfig.getConfig()).doesNotContainKey("drawnAt");
    }

    @Test
    void 후보_조회에_고정된_현재_시각을_사용한다() {
        when(participationRepository.findSportsDrawCandidateEventIds(NOW))
            .thenReturn(List.of(EVENT_ID));

        assertThat(service.findCandidateEventIds())
            .containsExactly(EVENT_ID);
    }

    @Test
    void 당첨자_2500명을_천명씩_나누어_선정한다() {
        putConfig("winningPrediction", "KOREA_WIN");
        putConfig("rewards", List.of(Map.of(
            "rewardId", "prediction",
            "target", "CORRECT_PREDICTIONS",
            "selection", "DRAW",
            "winnerCount", 2_500,
            "prizeName", "치킨 쿠폰"
        )));

        when(participationRepository.appendSportsRewardInBatch(
            EVENT_ID,
            "KOREA_WIN",
            "prediction",
            "CORRECT_PREDICTIONS",
            "DRAW",
            "치킨 쿠폰",
            true,
            1_000
        )).thenReturn(1_000, 1_000);

        when(participationRepository.appendSportsRewardInBatch(
            EVENT_ID,
            "KOREA_WIN",
            "prediction",
            "CORRECT_PREDICTIONS",
            "DRAW",
            "치킨 쿠폰",
            true,
            500
        )).thenReturn(500);

        service.drawEvent(EVENT_ID);

        verify(participationRepository, times(2))
            .appendSportsRewardInBatch(
                EVENT_ID,
                "KOREA_WIN",
                "prediction",
                "CORRECT_PREDICTIONS",
                "DRAW",
                "치킨 쿠폰",
                true,
                1_000
            );

        verify(participationRepository).appendSportsRewardInBatch(
            EVENT_ID,
            "KOREA_WIN",
            "prediction",
            "CORRECT_PREDICTIONS",
            "DRAW",
            "치킨 쿠폰",
            true,
            500
        );

        assertThat(gameConfig.getConfig()).containsKey("drawnAt");
    }

    private void putConfig(String key, Object value) {
        Map<String, Object> updated = new LinkedHashMap<>(gameConfig.getConfig());
        updated.put(key, value);

        gameConfig.changeConfig(gameConfig.getGame(), updated);
    }

    private Map<String, Object> config() {
        return Map.of(
            "predictionOptions",
            List.of("KOREA_WIN", "DRAW", "JAPAN_WIN"),
            "announcementAt", NOW.toString(),
            "rewards", List.of(
                Map.of(
                    "rewardId", "participation",
                    "target", "ALL_PARTICIPANTS",
                    "selection", "ALL",
                    "prizeName", "응원 5,000P"
                ),
                Map.of(
                    "rewardId", "prediction",
                    "target", "CORRECT_PREDICTIONS",
                    "selection", "DRAW",
                    "winnerCount", 30,
                    "prizeName", "치킨 쿠폰"
                )
            )
        );
    }

    private void assertError(Runnable action, ParticipationErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(
                ParticipationException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(expected)
            );
    }
}
