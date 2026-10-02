package com.newvent.participation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import com.newvent.participation.domain.EventParticipation;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class MyParticipationDetailRepositoryTest {

    @Autowired
    private EventParticipationRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long userId;
    private Long otherUserId;
    private Long eventId;
    private Long participationId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();

        Long adminId = jdbc.queryForObject(
                """
                INSERT INTO admins (login_id, password_hash, name)
                VALUES (?, 'test-password', '테스트 관리자')
                RETURNING id
                """,
                Long.class,
                "admin-" + suffix
        );

        userId = createUser("user-" + suffix);
        otherUserId = createUser("other-" + suffix);

        eventId = jdbc.queryForObject(
                """
                INSERT INTO events (owner_admin_id, title, grade)
                VALUES (?, '상세 조회 이벤트', 'NORMAL')
                RETURNING id
                """,
                Long.class,
                adminId
        );

        participationId = jdbc.queryForObject(
                """
                INSERT INTO event_participations (
                    user_id, event_id, submitted_data, result_data
                )
                VALUES (
                    ?, ?,
                    '{"prediction":"HOME_WIN"}'::jsonb,
                    '{"status":"PENDING"}'::jsonb
                )
                RETURNING id
                """,
                Long.class,
                userId,
                eventId
        );
    }

    @Test
    void 본인의_참여_기록과_저장된_데이터를_조회한다() {
        EventParticipation result = repository
                .findByIdAndUserId(participationId, userId)
                .orElseThrow();

        assertThat(result.getId()).isEqualTo(participationId);
        assertThat(result.getEvent().getId()).isEqualTo(eventId);
        assertThat(result.getEvent().getTitle()).isEqualTo("상세 조회 이벤트");
        assertThat(result.getSubmittedData()).containsEntry("prediction", "HOME_WIN");
        assertThat(result.getResultData()).containsEntry("status", "PENDING");
    }

    @Test
    void 다른_사용자의_참여_기록은_조회되지_않는다() {
        assertThat(repository.findByIdAndUserId(
                participationId, otherUserId
        )).isEmpty();
    }

    @Test
    void 해당_사용자의_참여_ID가_없으면_조회되지_않는다() {
        // 다른 사용자에게는 참여 기록이 없으므로 반드시 비어 있다.
        assertThat(repository.findByIdAndUserId(
                Long.MAX_VALUE, otherUserId
        )).isEmpty();
    }

    @Test
    void 종료되고_소프트_삭제된_이벤트도_참여_기록은_조회한다() {
        jdbc.update(
                """
                UPDATE events
                SET status = 'ENDED',
                    deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                eventId
        );

        EventParticipation result = repository
                .findByIdAndUserId(participationId, userId)
                .orElseThrow();

        assertThat(result.getEvent().deleted()).isTrue();
        assertThat(result.getResultData()).containsEntry("status", "PENDING");
    }

    private Long createUser(String loginId) {
        return jdbc.queryForObject(
                """
                INSERT INTO users (
                    login_id, password_hash, name,
                    email, plan, membership_grade
                )
                VALUES (?, 'test-password', '테스트 사용자', ?, 1, 'NORMAL')
                RETURNING id
                """,
                Long.class,
                loginId,
                loginId + "@example.com"
        );
    }
}
