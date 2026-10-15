package com.newvent.participation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.participation.repository.EventParticipationRepository;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
    SportsPredictionResultService.class,
    SportsPredictionDrawIntegrationTest.FixedClockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SportsPredictionDrawIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-15T14:00:00+09:00");

    @Autowired
    private SportsPredictionResultService service;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EventOwnerCheck ownerCheck;

    @MockitoSpyBean
    private EventParticipationRepository repository;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Long> userIds = new ArrayList<>();

    private Long adminId;
    private Long eventId;

    @BeforeEach
    void setUp() {
        adminId = jdbc.queryForObject(
            """
            INSERT INTO admins (login_id, password_hash, name)
            VALUES (?, 'test-password', '스포츠 테스트 관리자')
            RETURNING id
            """,
            Long.class,
            "sp-" + UUID.randomUUID()
        );

        eventId = jdbc.queryForObject(
            """
            INSERT INTO events (
                owner_admin_id, title, grade, status,
                start_date, end_date
            )
            VALUES (?, '스포츠 테스트', 'NORMAL', 'PUBLISHED', ?, ?)
            RETURNING id
            """,
            Long.class,
            adminId,
            NOW.minusDays(2),
            NOW.minusDays(1)
        );

        Long gameId = jdbc.queryForObject(
            "SELECT id FROM games WHERE code = 'SPORTS_PREDICTION'",
            Long.class
        );

        jdbc.update(
            """
            INSERT INTO event_game_configs (event_id, game_id, config)
            VALUES (?, ?, CAST(? AS jsonb))
            """,
            eventId,
            gameId,
            json(config())
        );
    }

    @AfterEach
    void cleanUp() {
        // 이벤트 삭제 시 해당 config와 participation은 FK CASCADE로 삭제된다.
        if (eventId != null) {
            jdbc.update("DELETE FROM events WHERE id = ?", eventId);
        }

        for (Long userId : userIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }

        if (adminId != null) {
            jdbc.update("DELETE FROM admins WHERE id = ?", adminId);
        }
    }

    @Test
    void 전원_혜택과_정답자_30명_추첨을_함께_처리한다() {
        addParticipants(40, "KOREA_WIN");
        addParticipants(15, "JAPAN_WIN");

        assertThat(service.findCandidateEventIds()).contains(eventId);

        service.drawEvent(eventId);

        assertThat(countReward("participation", "WON")).isEqualTo(55);
        assertThat(countReward("prediction", "WON")).isEqualTo(30);
        assertThat(countReward("prediction", "LOST")).isEqualTo(25);

        // 전체 참가자가 첫 번째 혜택에 당첨됐으므로 전체 상태는 모두 WON.
        assertThat(countStatus("WON")).isEqualTo(55);
        assertThat(countStatus("PENDING")).isZero();

        Long incorrectWinners = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM event_participations p
            WHERE p.event_id = ?
              AND p.submitted_data ->> 'prediction' <> 'KOREA_WIN'
              AND EXISTS (
                  SELECT 1
                  FROM jsonb_array_elements(
                      p.result_data -> 'rewards'
                  ) AS r(item)
                  WHERE r.item ->> 'rewardId' = 'prediction'
                    AND r.item ->> 'status' = 'WON'
              )
            """,
            Long.class,
            eventId
        );

        assertThat(incorrectWinners).isZero();

        Long validResults = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM event_participations
            WHERE event_id = ?
              AND jsonb_array_length(result_data -> 'rewards') = 2
              AND result_data ->> 'prizeName' = '응원 5,000P'
            """,
            Long.class,
            eventId
        );

        assertThat(validResults).isEqualTo(55);
        assertThat(drawnAt()).isNotNull();
        assertThat(service.findCandidateEventIds()).doesNotContain(eventId);
    }

    @Test
    void 정답자가_30명보다_적으면_정답자_전원이_추첨_혜택에_당첨된다() {
        addParticipants(3, "KOREA_WIN");
        addParticipants(2, "DRAW");

        service.drawEvent(eventId);

        assertThat(countReward("prediction", "WON")).isEqualTo(3);
        assertThat(countReward("prediction", "LOST")).isEqualTo(2);
    }

    @Test
    void 당첨된_혜택이_하나도_없으면_전체_LOST로_저장한다() {
        // 전원 혜택을 빼고 정답자 추첨 혜택만 설정한다.
        Map<String, Object> config = config();
        config.put("rewards", List.of(predictionReward()));

        jdbc.update(
            """
            UPDATE event_game_configs
            SET config = CAST(? AS jsonb)
            WHERE event_id = ?
            """,
            json(config),
            eventId
        );

        addParticipants(2, "KOREA_WIN");
        addParticipants(3, "JAPAN_WIN");

        service.drawEvent(eventId);

        assertThat(countStatus("WON")).isEqualTo(2);
        assertThat(countStatus("LOST")).isEqualTo(3);
    }

    @Test
    void 발표_전에는_실제_DB_결과가_PENDING으로_유지된다() {
        jdbc.update(
            """
            UPDATE event_game_configs
            SET config = jsonb_set(
                config,
                '{announcementAt}',
                to_jsonb(CAST(? AS text))
            )
            WHERE event_id = ?
            """,
            NOW.plusSeconds(1).toString(),
            eventId
        );

        addParticipants(2, "KOREA_WIN");

        service.drawEvent(eventId);

        assertThat(countStatus("PENDING")).isEqualTo(2);
        assertThat(drawnAt()).isNull();
    }

    @Test
    void 재실행해도_당첨자와_결과가_바뀌지_않는다() {
        addParticipants(40, "KOREA_WIN");

        service.drawEvent(eventId);

        List<String> firstResults = results();
        String firstDrawnAt = drawnAt();

        service.drawEvent(eventId);

        assertThat(results()).isEqualTo(firstResults);
        assertThat(drawnAt()).isEqualTo(firstDrawnAt);
    }

    @Test
    void 두번째_혜택_처리에_실패하면_첫번째_혜택까지_롤백된다() {
        addParticipants(3, "KOREA_WIN");
        addParticipants(2, "JAPAN_WIN");

        List<String> before = results();

        // 첫 번째 혜택은 실제 SQL로 처리한 뒤,
        // 두 번째 혜택에서 예외를 발생시킨다.
        doThrow(new IllegalStateException("강제 실패"))
            .when(repository)
            .appendSportsRewardInBatch(
                eq(eventId),
                eq("KOREA_WIN"),
                eq("prediction"),
                anyString(),
                anyString(),
                anyString(),
                anyBoolean(),
                anyInt()
            );

        assertThatThrownBy(() -> service.drawEvent(eventId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("강제 실패");

        assertThat(results()).isEqualTo(before);
        assertThat(countStatus("PENDING")).isEqualTo(5);
        assertThat(drawnAt()).isNull();
    }

    @Test
    void 참가자가_없어도_발표_완료_표시를_저장한다() {
        service.drawEvent(eventId);

        assertThat(drawnAt()).isNotNull();
        assertThat(service.findCandidateEventIds()).doesNotContain(eventId);
    }

    private void addParticipants(int count, String prediction) {
        for (int index = 0; index < count; index++) {
            String loginId = "sp-" + UUID.randomUUID();

            Long userId = jdbc.queryForObject(
                """
                INSERT INTO users (
                    login_id, password_hash, name,
                    email, plan, membership_grade
                )
                VALUES (?, 'test-password', '스포츠 테스트 사용자',
                        ?, 1, 'NORMAL')
                RETURNING id
                """,
                Long.class,
                loginId,
                loginId + "@example.com"
            );

            userIds.add(userId);

            jdbc.update(
                """
                INSERT INTO event_participations (
                    user_id, event_id, submitted_data, result_data
                )
                VALUES (
                    ?, ?,
                    jsonb_build_object('prediction', CAST(? AS text)),
                    '{"status":"PENDING"}'
                )
                """,
                userId,
                eventId,
                prediction
            );
        }
    }

    @Test
    void 천명이_넘어도_모든_혜택과_최종_상태를_처리한다() {
        addParticipants(1_005, "KOREA_WIN");
        addParticipants(200, "JAPAN_WIN");

        service.drawEvent(eventId);

        assertThat(countReward("participation", "WON")).isEqualTo(1_205);
        assertThat(countReward("prediction", "WON")).isEqualTo(30);
        assertThat(countReward("prediction", "LOST")).isEqualTo(1_175);

        assertThat(countStatus("WON")).isEqualTo(1_205);
        assertThat(countStatus("PENDING")).isZero();

        Long completeResults = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM event_participations
            WHERE event_id = ?
              AND jsonb_array_length(result_data -> 'rewards') = 2
              AND result_data ->> 'prizeName' = '응원 5,000P'
            """,
            Long.class,
            eventId
        );

        assertThat(completeResults).isEqualTo(1_205);
        assertThat(drawnAt()).isNotNull();

        List<String> firstResults = results();

        service.drawEvent(eventId);

        assertThat(results()).isEqualTo(firstResults);
    }

    private long countReward(String rewardId, String status) {
        Long count = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM event_participations p
            WHERE p.event_id = ?
              AND EXISTS (
                  SELECT 1
                  FROM jsonb_array_elements(
                      p.result_data -> 'rewards'
                  ) AS r(item)
                  WHERE r.item ->> 'rewardId' = ?
                    AND r.item ->> 'status' = ?
              )
            """,
            Long.class,
            eventId,
            rewardId,
            status
        );

        return count;
    }

    private long countStatus(String status) {
        Long count = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM event_participations
            WHERE event_id = ?
              AND result_data ->> 'status' = ?
            """,
            Long.class,
            eventId,
            status
        );

        return count;
    }

    private List<String> results() {
        return jdbc.queryForList(
            """
            SELECT CAST(result_data AS text)
            FROM event_participations
            WHERE event_id = ?
            ORDER BY id
            """,
            String.class,
            eventId
        );
    }

    private String drawnAt() {
        return jdbc.queryForObject(
            """
            SELECT config ->> 'drawnAt'
            FROM event_game_configs
            WHERE event_id = ?
            """,
            String.class,
            eventId
        );
    }

    private Map<String, Object> config() {
        return new java.util.LinkedHashMap<>(Map.of(
            "predictionOptions",
            List.of("KOREA_WIN", "DRAW", "JAPAN_WIN"),
            "announcementAt", NOW.toString(),
            "winningPrediction", "KOREA_WIN",
            "rewards", List.of(
                Map.of(
                    "rewardId", "participation",
                    "target", "ALL_PARTICIPANTS",
                    "selection", "ALL",
                    "prizeName", "응원 5,000P"
                ),
                predictionReward()
            )
        ));
    }

    private Map<String, Object> predictionReward() {
        return Map.of(
            "rewardId", "prediction",
            "target", "CORRECT_PREDICTIONS",
            "selection", "DRAW",
            "winnerCount", 30,
            "prizeName", "치킨 쿠폰"
        );
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.toInstant(), NOW.getOffset());
        }
    }
}
