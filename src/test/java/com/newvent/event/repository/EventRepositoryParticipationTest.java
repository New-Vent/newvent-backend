package com.newvent.event.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

// 영구 삭제 보호용 쿼리(hasParticipations)를 <b>실제 PostgreSQL 에서</b> 돌린다.
// JPQL 이 참여 엔티티를 이름으로만 참조하고 SELECT 절에 불리언 식(COUNT(p) > 0)을 쓰므로, 저장소를 mock 으로 바꾸는 EventServiceTest 로는 쿼리가 실제로 맞는지 알 수 없다
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(com.newvent.common.config.JpaAuditingConfig.class)
class EventRepositoryParticipationTest {

    @Autowired
    private EventRepository events;

    @Autowired
    private TestEntityManager tem;

    private Long participated;
    private Long participatedByTwo;
    private Long withoutParticipation;

    @BeforeEach
    void seed() {
        EntityManager em = tem.getEntityManager();
        Long admin = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES ('part_admin','x','관리자') RETURNING id")
                .getSingleResult()).longValue();
        Long userA = insertUser(em, "part_user_a");
        Long userB = insertUser(em, "part_user_b");

        participated = insertEvent(em, admin, "참여 있음");
        participatedByTwo = insertEvent(em, admin, "참여 두 건");
        withoutParticipation = insertEvent(em, admin, "참여 없음");

        insertParticipation(em, participated, userA);
        insertParticipation(em, participatedByTwo, userA);
        insertParticipation(em, participatedByTwo, userB);
    }

    private static Long insertUser(EntityManager em, String loginId) {
        return ((Number) em.createNativeQuery(
                        "INSERT INTO users (login_id, password_hash, name, email, plan, membership_grade) "
                                + "VALUES (?1, 'x', '점검', ?2, 20000, 'NORMAL') RETURNING id")
                .setParameter(1, loginId)
                .setParameter(2, loginId + "@test.newvent.io")
                .getSingleResult()).longValue();
    }

    private static Long insertEvent(EntityManager em, Long admin, String title) {
        return ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade, status) "
                                + "VALUES (?1, ?2, 'NORMAL', 'DRAFT') RETURNING id")
                .setParameter(1, admin)
                .setParameter(2, title)
                .getSingleResult()).longValue();
    }

    private static void insertParticipation(EntityManager em, Long eventId, Long userId) {
        em.createNativeQuery("INSERT INTO event_participations (event_id, user_id) VALUES (?1, ?2)")
                .setParameter(1, eventId)
                .setParameter(2, userId)
                .executeUpdate();
    }

    @Test
    @DisplayName("참여 기록이 있는 이벤트는 true 다")
    void 참여_기록이_있으면_true() {
        assertTrue(events.hasParticipations(participated));
    }

    @Test
    @DisplayName("참여가 여러 건이어도 true 다")
    void 참여가_여러_건이어도_true() {
        assertTrue(events.hasParticipations(participatedByTwo));
    }

    @Test
    @DisplayName("참여 기록이 없는 이벤트는 false 다 — 다른 이벤트의 참여는 세지 않는다")
    void 참여_기록이_없으면_false() {
        assertFalse(events.hasParticipations(withoutParticipation));
    }

    @Test
    @DisplayName("없는 이벤트 번호도 false 다")
    void 없는_이벤트는_false() {
        assertFalse(events.hasParticipations(-1L));
    }
}
