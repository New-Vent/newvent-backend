package com.newvent.event.service;


import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.newvent.common.config.JpaAuditingConfig;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.repository.EventRepository;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        EventExpirationService.class,
        JpaAuditingConfig.class,
        EventExpirationServiceTest.FixedClockConfig.class
})
class EventExpirationServiceTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-30T13:00:00+09:00");

    private static final OffsetDateTime OLD_UPDATED_AT =
            NOW.minusDays(1);

    @Autowired
    private EventExpirationService expirationService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TestEntityManager tem;

    private Long adminId;

    @BeforeEach
    void setUp() {
        // 기존 더미 데이터가 변경 건수에 영향을 주지 않도록 먼저 처리한다.
        // 이 변경도 테스트 트랜잭션 종료 시 롤백된다.
        expirationService.endExpiredEvents();

        adminId = ((Number) tem.getEntityManager()
                .createNativeQuery("""
                        INSERT INTO admins
                            (login_id, password_hash, name)
                        VALUES (?1, ?2, ?3)
                        RETURNING id
                        """)
                .setParameter(1, "expiration_" + UUID.randomUUID())
                .setParameter(2, "test-password-hash")
                .setParameter(3, "테스트 관리자")
                .getSingleResult()).longValue();
    }

    @Test
    void 종료_시각이_지난_게시_이벤트만_자동_종료한다() {
        Long expired = seed(
                EventStatus.PUBLISHED, NOW.minusMinutes(1), false);

        Long ongoing = seed(
                EventStatus.PUBLISHED, NOW.plusMinutes(1), false);

        Long boundary = seed(
                EventStatus.PUBLISHED, NOW, false);

        Long draft = seed(
                EventStatus.DRAFT, NOW.minusMinutes(1), false);

        Long ended = seed(
                EventStatus.ENDED, NOW.minusMinutes(1), false);

        Long deleted = seed(
                EventStatus.PUBLISHED, NOW.minusMinutes(1), true);

        Long noEndDate = seed(
                EventStatus.PUBLISHED, null, false);

        int updated = expirationService.endExpiredEvents();

        assertThat(updated).isEqualTo(1);

        Event expiredEvent = find(expired);
        assertThat(expiredEvent.getStatus()).isEqualTo(EventStatus.ENDED);
        assertThat(expiredEvent.getUpdatedAt().toInstant())
                .isEqualTo(NOW.toInstant());

        // 상태만 변경하고 이벤트 기간은 유지한다.
        assertThat(expiredEvent.getEndDate().toInstant())
                .isEqualTo(NOW.minusMinutes(1).toInstant());

        assertUnchanged(ongoing, EventStatus.PUBLISHED);
        assertUnchanged(boundary, EventStatus.PUBLISHED);
        assertUnchanged(draft, EventStatus.DRAFT);
        assertUnchanged(ended, EventStatus.ENDED);
        assertUnchanged(deleted, EventStatus.PUBLISHED);
        assertUnchanged(noEndDate, EventStatus.PUBLISHED);

        // 이미 처리한 이벤트는 다시 변경하지 않는다.
        assertThat(expirationService.endExpiredEvents()).isZero();
    }

    private void assertUnchanged(Long id, EventStatus status) {
        Event event = find(id);

        assertThat(event.getStatus()).isEqualTo(status);
        assertThat(event.getUpdatedAt().toInstant())
                .isEqualTo(OLD_UPDATED_AT.toInstant());
    }

    private Event find(Long id) {
        return eventRepository.findById(id).orElseThrow();
    }

    private Long seed(
            EventStatus status,
            OffsetDateTime endDate,
            boolean deleted) {

        return ((Number) tem.getEntityManager()
                .createNativeQuery("""
                        INSERT INTO events (
                            owner_admin_id,
                            title,
                            grade,
                            status,
                            start_date,
                            end_date,
                            deleted_at,
                            updated_at
                        )
                        VALUES (
                            ?1, ?2, 'NORMAL', ?3,
                            CAST(?4 AS timestamptz),
                            CAST(?5 AS timestamptz),
                            CAST(?6 AS timestamptz),
                            CAST(?7 AS timestamptz)
                        )
                        RETURNING id
                        """)
                .setParameter(1, adminId)
                .setParameter(2, "자동 종료 테스트")
                .setParameter(3, status.name())
                .setParameter(4, NOW.minusDays(2).toString())
                .setParameter(5, endDate == null ? null : endDate.toString())
                .setParameter(6, deleted ? NOW.minusHours(1).toString() : null)
                .setParameter(7, OLD_UPDATED_AT.toString())
                .getSingleResult()).longValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse("2026-09-30T04:00:00Z"),
                    ZoneId.of("Asia/Seoul"));
        }
    }
}
