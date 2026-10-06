package com.newvent.event.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventProgress;
import com.newvent.event.domain.EventStatus;

/**
 * 관리자 목록 쿼리를 <b>실제 PostgreSQL 에서</b> 돌린다.
 *
 * ★ 왜 이 테스트인가
 *   (:periodFrom IS NULL OR …) 로 쓴 쿼리가 PostgreSQL 에서 항상 실패했다
 *   (could not determine data type of parameter). EventServiceTest 는 저장소를 목으로 바꿔서 못 봤다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(com.newvent.common.config.JpaAuditingConfig.class)
class EventRepositoryAdminSearchTest {

    private static final OffsetDateTime OCT_1 = OffsetDateTime.parse("2026-10-01T00:00:00+09:00");
    private static final OffsetDateTime OCT_31 = OffsetDateTime.parse("2026-10-31T23:59:59+09:00");
    private static final OffsetDateTime NOV_1 = OffsetDateTime.parse("2026-11-01T00:00:00+09:00");
    private static final OffsetDateTime NOV_30 = OffsetDateTime.parse("2026-11-30T23:59:59+09:00");
    private static final OffsetDateTime OCT_15 = OffsetDateTime.parse("2026-10-15T12:00:00+09:00");
    private static final OffsetDateTime NOV_15 = OffsetDateTime.parse("2026-11-15T12:00:00+09:00");

    @Autowired
    private EventRepository events;

    @Autowired
    private EventVersionRepository versions;

    @Autowired
    private TestEntityManager tem;

    private Long october, november, noDates, deleted;

    @BeforeEach
    void seed() {
        EntityManager em = tem.getEntityManager();
        Long admin = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES ('search_admin','x','관리자') RETURNING id")
                .getSingleResult()).longValue();
        october = insert(em, admin, "검색확인 가을 응원", OCT_1, OCT_31, "DRAFT", false);
        november = insert(em, admin, "검색확인 겨울 특가", NOV_1, NOV_30, "PUBLISHED", false);
        noDates = insert(em, admin, "검색확인 날짜 없음", null, null, "DRAFT", false);
        deleted = insert(em, admin, "검색확인 지운 것", OCT_1, OCT_31, "DRAFT", true);
    }

    private static Long insert(EntityManager em, Long admin, String title, OffsetDateTime start,
                               OffsetDateTime end, String status, boolean deleted) {
        return ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade, start_date, end_date, status, deleted_at) "
                        + "VALUES (?1, ?2, 'NORMAL', ?3, ?4, ?5, " + (deleted ? "now()" : "NULL") + ") RETURNING id")
                .setParameter(1, admin)
                .setParameter(2, title)
                .setParameter(3, start)
                .setParameter(4, end)
                .setParameter(5, status)
                .getSingleResult()).longValue();
    }

    /** 이번 테스트가 심은 것만 — DB 에 다른 이벤트(시드)가 있어도 흔들리지 않게 */
    private List<Long> search(String name, EventStatus status, OffsetDateTime from, OffsetDateTime to) {
        return search(name, status, null, OCT_15, from, to);
    }

    private List<Long> search(String name, EventStatus status, EventProgress progress, OffsetDateTime now,
                              OffsetDateTime from, OffsetDateTime to) {
        String pattern = "%검색확인" + (name == null ? "" : "%" + name) + "%";
        return events.findAdminEvents(pattern, null, status, progress, now, from, to, PageRequest.of(0, 50))
                .getContent().stream().map(Event::getId).sorted().toList();
    }

    @Test
    @DisplayName("조건이 없으면 삭제되지 않은 것 전부 — 날짜가 비어 있어도 나온다")
    void 조건_없음() {
        assertEquals(List.of(october, november, noDates), search(null, null, null, null));
    }

    @Test
    @DisplayName("이름 · 상태 조건")
    void 이름과_상태() {
        assertEquals(List.of(october), search("가을", null, null, null));
        assertEquals(List.of(november), search(null, EventStatus.PUBLISHED, null, null));
        assertEquals(List.of(october, noDates), search(null, EventStatus.DRAFT, null, null));
    }

    @Test
    @DisplayName("기간은 구간이 겹치는 것만 — 한쪽만 줘도 되고, 날짜가 빈 이벤트는 빠진다")
    void 기간() {
        assertEquals(List.of(october), search(null, null, OCT_1, OCT_31));
        assertEquals(List.of(november), search(null, null, NOV_1, null));
        assertEquals(List.of(october), search(null, null, null, OCT_31));
        assertEquals(List.of(october, november), search(null, null, OCT_1, NOV_30));
    }

    @Test
    @DisplayName("모든 조건을 같이")
    void 전부() {
        assertEquals(List.of(november), search("겨울", EventStatus.PUBLISHED, OCT_1, NOV_30));
        assertEquals(List.of(), search("겨울", EventStatus.DRAFT, OCT_1, NOV_30));
    }

    @Test
    @DisplayName("진행상태는 now 와 기간으로 판정한다 — 날짜가 빈 이벤트는 진행 중으로 본다")
    void 진행상태() {
        assertEquals(List.of(october, noDates), search(null, null, EventProgress.ONGOING, OCT_15, null, null));
        assertEquals(List.of(november), search(null, null, EventProgress.UPCOMING, OCT_15, null, null));
        assertEquals(List.of(), search(null, null, EventProgress.ENDED, OCT_15, null, null));
        assertEquals(List.of(october), search(null, null, EventProgress.ENDED, NOV_15, null, null));
        assertEquals(List.of(november), search(null, EventStatus.PUBLISHED, EventProgress.ONGOING, NOV_15, null, null));
    }

    @Test
    @DisplayName("ID 가 오면 이름이 안 맞아도 그 ID 는 나온다 — 삭제된 것은 빠진다")
    void ID_검색() {
        assertEquals(List.of(october), idSearch(october));
        assertEquals(List.of(), idSearch(deleted));
    }

    private List<Long> idSearch(Long id) {
        return events.findAdminEvents("%이름에없는검색어%", id, null, null, OCT_15, null, null, PageRequest.of(0, 50))
                .getContent().stream().map(Event::getId).toList();
    }

    @Test
    @DisplayName("목록은 게시 버전을 함께 가져오고, 최신 버전 번호는 이벤트별로 모은다")
    void 버전_번호() {
        EntityManager em = tem.getEntityManager();
        insertVersion(em, november, 1);
        Long published = insertVersion(em, november, 2);
        insertVersion(em, november, 3);
        em.createNativeQuery("UPDATE events SET published_version_id = ?1 WHERE id = ?2")
                .setParameter(1, published)
                .setParameter(2, november)
                .executeUpdate();
        tem.clear();

        Event found = events.findAdminEvents("%검색확인 겨울%", null, null, null, OCT_15, null, null,
                PageRequest.of(0, 50)).getContent().get(0);
        List<Object[]> latest = versions.findLatestVersionNos(List.of(october, november));

        assertEquals(2, found.getPublishedVersion().getVersionNo());
        assertEquals(1, latest.size());
        assertEquals(november, latest.get(0)[0]);
        assertEquals(3, latest.get(0)[1]);
    }

    private static Long insertVersion(EntityManager em, Long eventId, int versionNo) {
        return ((Number) em.createNativeQuery(
                        "INSERT INTO event_versions (event_id, version_no, html_content) VALUES (?1, ?2, '<p></p>') "
                        + "RETURNING id")
                .setParameter(1, eventId)
                .setParameter(2, versionNo)
                .getSingleResult()).longValue();
    }
}
