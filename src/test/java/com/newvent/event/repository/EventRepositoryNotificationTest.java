package com.newvent.event.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;

/**
 * findStartingEvents 를 <b>실제 PostgreSQL 에서</b> 돌린다.
 *
 * ★ 왜 이 테스트인가 — PR #114 리뷰(tnqlsqkr): EventExpirationJob(자동 종료)과
 *   별도 스케줄러라, 서버 재시작 등으로 종료일이 이미 지났는데도 아직 ENDED로
 *   안 바뀐 PUBLISHED 이벤트가 "시작" 알림 대상에 섞여 들어올 수 있었다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(com.newvent.common.config.JpaAuditingConfig.class)
class EventRepositoryNotificationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-01T00:00:00+09:00");

    @Autowired
    private EventRepository events;

    @Autowired
    private TestEntityManager tem;

    @Test
    @DisplayName("종료일이 이미 지난 PUBLISHED 이벤트는 시작 알림 대상에서 제외된다")
    void 종료일이_지나면_시작_알림_대상에서_제외된다() {
        EntityManager em = tem.getEntityManager();
        Long admin = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES ('noti_admin','x','관리자') RETURNING id")
                .getSingleResult()).longValue();

        // 아직 끝나지 않은, 정상적으로 시작 알림을 받아야 하는 이벤트
        Long ongoing = insert(em, admin, "알림테스트 진행중", NOW.minusDays(1), NOW.plusDays(5));

        // 종료일이 이미 지났는데도 아직 ENDED로 안 바뀐(EventExpirationJob 미처리) 이벤트
        Long alreadyEnded = insert(em, admin, "알림테스트 이미종료", NOW.minusDays(5), NOW.minusMinutes(1));

        List<Long> result = events.findStartingEvents(EventStatus.PUBLISHED, NOW).stream()
                .map(Event::getId)
                .toList();

        assertThat(result).contains(ongoing);
        assertThat(result).doesNotContain(alreadyEnded);
    }

    private static Long insert(EntityManager em, Long admin, String title,
                               OffsetDateTime start, OffsetDateTime end) {
        return ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade, start_date, end_date, status) "
                        + "VALUES (?1, ?2, 'NORMAL', ?3, ?4, 'PUBLISHED') RETURNING id")
                .setParameter(1, admin)
                .setParameter(2, title)
                .setParameter(3, start)
                .setParameter(4, end)
                .getSingleResult()).longValue();
    }
}
