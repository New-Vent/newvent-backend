package com.newvent.participation.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.newvent.event.domain.Event;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;

/**
 * 관리자 참여자 조회 쿼리를 <b>실제 PostgreSQL 에서</b> 돌려 본다.
 *
 * ★★ 왜 단위 테스트로는 부족한가 — 이 두 쿼리는 깨질 자리가 JPQL 안에 있다.
 *     ① {@code :resultStatus IS NULL} 분기
 *        Hibernate 가 파라미터 타입을 못 정하면 기동 시점이 아니라 <b>호출 시점</b>에 터진다.
 *        서비스 테스트는 레포지토리를 mock 하므로 끝까지 모른다.
 *     ② {@code function('jsonb_extract_path_text', …)}
 *        PostgreSQL 전용 함수다. H2 로 바꿔 돌리면 통과하고 운영에서 깨진다.
 *        그래서 @AutoConfigureTestDatabase(replace = NONE) 로 compose PG 를 그대로 쓴다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventParticipationAdminQueryTest {

    @Autowired
    private EventParticipationRepository repository;

    @Autowired
    private TestEntityManager tem;

    private Event event;
    private Event otherEvent;

    @BeforeEach
    void 참여를_심는다() {
        event = seedEvent("참여 조회 이벤트");
        otherEvent = seedEvent("남의 이벤트");

        participate(event, Map.of(), Map.of("status", "WON", "prizeName", "데이터 5GB 쿠폰"));
        participate(event, Map.of("phoneNumber", "01012345678"), Map.of("status", "LOST"));
        participate(event, Map.of(), Map.of("status", "PENDING"));
        participate(event, Map.of(), Map.of());            // 결과가 없는 방식
        participate(otherEvent, Map.of(), Map.of("status", "WON"));

        tem.flush();
    }

    @Test
    @DisplayName("필터가 null 이면 그 이벤트의 전체를 최신순으로 — 다른 이벤트는 섞이지 않는다")
    void 필터없이_전체를_조회한다() {
        Page<EventParticipation> page =
                repository.findByEventId(event.getId(), null, PageRequest.of(0, 20));

        assertEquals(4, page.getTotalElements(), "이 이벤트의 참여만 4건이다");
        assertTrue(page.getContent().stream()
                        .allMatch(p -> p.getEvent().getId().equals(event.getId())),
                "다른 이벤트의 참여가 섞이면 안 된다");
    }

    @Test
    @DisplayName("resultStatus 로 거른다 — 당첨자 명단이 이 경로다")
    void 당첨만_조회한다() {
        Page<EventParticipation> page =
                repository.findByEventId(event.getId(), "WON", PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("데이터 5GB 쿠폰", page.getContent().getFirst().getResultData().get("prizeName"));
    }

    @Test
    @DisplayName("결과가 없는 참여는 상태 필터에 걸리지 않는다 — 빈 result_data 다")
    void 결과없는참여는_필터에안걸린다() {
        assertEquals(1, repository.findByEventId(event.getId(), "LOST", PageRequest.of(0, 20))
                .getTotalElements());
        assertEquals(1, repository.findByEventId(event.getId(), "PENDING", PageRequest.of(0, 20))
                .getTotalElements());
        assertEquals(0, repository.findByEventId(event.getId(), "JACKPOT", PageRequest.of(0, 20))
                .getTotalElements());
    }

    @Test
    @DisplayName("집계는 한 이벤트 범위다 — 전체 · 당첨 · 대기를 한 번에")
    void 집계는_이벤트범위다() {
        ParticipationSummaryProjection counts = repository.findSummaryByEventId(event.getId());

        assertEquals(4, counts.getTotalParticipationCount());
        assertEquals(1, counts.getRewardCount());
        assertEquals(1, counts.getPendingCount());
    }

    @Test
    @DisplayName("참여가 없는 이벤트도 0 으로 답한다 — null 이 아니다")
    void 참여없으면_0이다() {
        Event empty = seedEvent("참여 없는 이벤트");
        tem.flush();

        ParticipationSummaryProjection counts = repository.findSummaryByEventId(empty.getId());

        assertEquals(0, counts.getTotalParticipationCount());
        assertEquals(0, counts.getRewardCount());
        assertEquals(0, counts.getPendingCount());
    }

    @Test
    @DisplayName("페이지를 나눠도 전체 건수는 유지된다")
    void 페이징이_동작한다() {
        Page<EventParticipation> first =
                repository.findByEventId(event.getId(), null, PageRequest.of(0, 2));

        assertEquals(2, first.getContent().size());
        assertEquals(4, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
    }

    // ── 거푸집 ────────────────────────────────────────────────────

    private Event seedEvent(String title) {
        EntityManager em = tem.getEntityManager();
        Long adminId = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, "admin-" + UUID.randomUUID())
                .setParameter(2, "x")
                .setParameter(3, "관리자")
                .getSingleResult()).longValue();
        Long eventId = ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade) VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, adminId)
                .setParameter(2, title)
                .setParameter(3, "NORMAL")
                .getSingleResult()).longValue();
        return em.getReference(Event.class, eventId);
    }

    private void participate(Event target, Map<String, Object> submitted, Map<String, Object> result) {
        String unique = UUID.randomUUID().toString();
        User user = new User(
                "part-" + unique, "x", "참여자", unique + "@example.com",
                "01012345678", 50000, MembershipGrade.NORMAL);
        ReflectionTestUtils.setField(user, "createdAt", OffsetDateTime.now());
        ReflectionTestUtils.setField(user, "updatedAt", OffsetDateTime.now());
        tem.persist(user);

        EventParticipation participation =
                EventParticipation.create(target, user, submitted, result);
        // ★ @DataJpaTest 슬라이스는 JPA Auditing(JpaAuditingConfig)을 켜지 않는다 —
        //   @CreatedDate 가 비어 들어가 NOT NULL 로 거부된다. 그래서 직접 넣는다.
        ReflectionTestUtils.setField(participation, "createdAt", OffsetDateTime.now());
        tem.persist(participation);
    }
}
