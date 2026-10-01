package com.newvent.participation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.newvent.participation.domain.EventParticipation;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class EventParticipationRepositoryTest {

    @Autowired
    private EventParticipationRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long adminId;
    private Long userId;
    private Long otherUserId;

    private Long completedId;
    private Long pendingId;
    private Long lostId;
    private Long wonId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();

        adminId = jdbc.queryForObject(
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

        completedId = createParticipation(
                userId, "단순 참여", "{}", "2026-10-01T10:00:00+09:00"
        );
        pendingId = createParticipation(
                userId,
                "결과 대기",
                "{\"status\":\"PENDING\"}",
                "2026-10-01T11:00:00+09:00"
        );
        lostId = createParticipation(
                userId,
                "미당첨",
                "{\"status\":\"LOST\"}",
                "2026-10-01T12:00:00+09:00"
        );
        wonId = createParticipation(
                userId,
                "당첨",
                "{\"status\":\"WON\",\"prizeName\":\"커피 쿠폰\"}",
                "2026-10-01T13:00:00+09:00"
        );

        createParticipation(
                otherUserId,
                "다른 사용자 당첨",
                "{\"status\":\"WON\",\"prizeName\":\"다른 쿠폰\"}",
                "2026-10-01T14:00:00+09:00"
        );
        createParticipation(
                otherUserId,
                "다른 사용자 대기",
                "{\"status\":\"PENDING\"}",
                "2026-10-01T15:00:00+09:00"
        );
    }

    @Test
    void 본인_참여만_최신순으로_페이징한다() {
        Page<EventParticipation> first =
                repository.findMyParticipations(
                        userId, PageRequest.of(0, 2)
                );

        Page<EventParticipation> second =
                repository.findMyParticipations(
                        userId, PageRequest.of(1, 2)
                );

        assertThat(first.getTotalElements()).isEqualTo(4);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent())
                .extracting(EventParticipation::getId)
                .containsExactly(wonId, lostId);

        assertThat(second.getContent())
                .extracting(EventParticipation::getId)
                .containsExactly(pendingId, completedId);
    }

    @Test
    void 당첨_목록에는_본인의_WON_기록만_포함한다() {
        Page<EventParticipation> result =
                repository.findMyParticipationsByResultStatus(
                        userId, "WON", PageRequest.of(0, 10)
                );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(EventParticipation::getId)
                .containsExactly(wonId);

        assertThat(result.getContent().getFirst().getEvent().getTitle())
                .isEqualTo("당첨");
    }

    @Test
    void 당첨_목록의_totalElements는_페이지_크기와_관계없다() {
        Long newerWonId = createParticipation(
                userId,
                "추가 당첨",
                "{\"status\":\"WON\",\"prizeName\":\"추가 쿠폰\"}",
                "2026-10-01T16:00:00+09:00"
        );

        Page<EventParticipation> first =
                repository.findMyParticipationsByResultStatus(
                        userId, "WON", PageRequest.of(0, 1)
                );

        Page<EventParticipation> second =
                repository.findMyParticipationsByResultStatus(
                        userId, "WON", PageRequest.of(1, 1)
                );

        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent())
                .extracting(EventParticipation::getId)
                .containsExactly(newerWonId);
        assertThat(second.getContent())
                .extracting(EventParticipation::getId)
                .containsExactly(wonId);
    }

    @Test
    void 요약은_본인_전체_기록을_집계하고_빈_결과는_대기에서_제외한다() {
        assertThat(repository.countByUserId(userId)).isEqualTo(4);
        assertThat(repository.countByUserIdAndResultStatus(userId, "WON"))
                .isEqualTo(1);
        assertThat(repository.countByUserIdAndResultStatus(
                userId, "PENDING"
        )).isEqualTo(1);
    }

    @Test
    void 참여_시각이_같으면_ID_내림차순으로_정렬한다() {
        Long newerId = createParticipation(
                userId,
                "동일 시각 참여",
                "{}",
                "2026-10-01T13:00:00+09:00"
        );

        Page<EventParticipation> result =
                repository.findMyParticipations(
                        userId, PageRequest.of(0, 2)
                );

        assertThat(result.getContent())
                .extracting(EventParticipation::getId)
                .containsExactly(newerId, wonId);
    }

    @Test
    void 기록이_없는_사용자는_빈_목록과_0을_반환한다() {
        Long emptyUserId = createUser(
                "empty-" + UUID.randomUUID()
        );

        assertThat(repository.findMyParticipations(
                emptyUserId, PageRequest.of(0, 10)
        ).getContent()).isEmpty();

        assertThat(repository.findMyParticipationsByResultStatus(
                emptyUserId, "WON", PageRequest.of(0, 10)
        ).getTotalElements()).isZero();

        assertThat(repository.countByUserId(emptyUserId)).isZero();
        assertThat(repository.countByUserIdAndResultStatus(
                emptyUserId, "WON"
        )).isZero();
        assertThat(repository.countByUserIdAndResultStatus(
                emptyUserId, "PENDING"
        )).isZero();
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

    private Long createParticipation(
            Long participantUserId,
            String title,
            String resultJson,
            String createdAt
    ) {
        Long eventId = jdbc.queryForObject(
                """
                INSERT INTO events (owner_admin_id, title, grade)
                VALUES (?, ?, 'NORMAL')
                RETURNING id
                """,
                Long.class,
                adminId,
                title
        );

        return jdbc.queryForObject(
                """
                INSERT INTO event_participations (
                    user_id, event_id, result_data, created_at
                )
                VALUES (?, ?, CAST(? AS jsonb), ?)
                RETURNING id
                """,
                Long.class,
                participantUserId,
                eventId,
                resultJson,
                OffsetDateTime.parse(createdAt)
        );
    }
}
