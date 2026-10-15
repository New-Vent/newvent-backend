package com.newvent.event.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

// 관리자 목록 썸네일용 쿼리(findLatestVersionHtmls)를 <b>실제 PostgreSQL 에서</b> 돌린다.
// 서브쿼리(MAX(version_no))가 들어간 JPQL 이라 EventServiceTest처럼 저장소를 mock으로 바꾸면 쿼리가 실제로 맞는지 알 수 없기 때문
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(com.newvent.common.config.JpaAuditingConfig.class)
class EventVersionRepositoryLatestHtmlTest {

    @Autowired
    private EventVersionRepository versions;

    @Autowired
    private TestEntityManager tem;

    private Long withThreeVersions;
    private Long withOneVersion;
    private Long withoutVersion;

    @BeforeEach
    void seed() {
        EntityManager em = tem.getEntityManager();
        Long admin = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES ('thumb_admin','x','관리자') RETURNING id")
                .getSingleResult()).longValue();
        withThreeVersions = insertEvent(em, admin, "썸네일확인 버전 셋");
        withOneVersion = insertEvent(em, admin, "썸네일확인 버전 하나");
        withoutVersion = insertEvent(em, admin, "썸네일확인 버전 없음");

        insertVersion(em, withThreeVersions, 1, "<p>v1</p>");
        insertVersion(em, withThreeVersions, 2, "<p>v2</p>");
        insertVersion(em, withThreeVersions, 3, "<p>v3 최신</p>");
        insertVersion(em, withOneVersion, 1, "<p>only</p>");
    }

    private static Long insertEvent(EntityManager em, Long admin, String title) {
        return ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade, status) "
                                + "VALUES (?1, ?2, 'NORMAL', 'DRAFT') RETURNING id")
                .setParameter(1, admin)
                .setParameter(2, title)
                .getSingleResult()).longValue();
    }

    private static void insertVersion(EntityManager em, Long eventId, int versionNo, String html) {
        em.createNativeQuery("INSERT INTO event_versions (event_id, version_no, html_content) VALUES (?1, ?2, ?3)")
                .setParameter(1, eventId)
                .setParameter(2, versionNo)
                .setParameter(3, html)
                .executeUpdate();
    }

    private Map<Long, String> htmls(Long... ids) {
        Map<Long, String> result = new HashMap<>();
        for (Object[] row : versions.findLatestVersionHtmls(List.of(ids))) {
            result.put((Long) row[0], (String) row[1]);
        }
        return result;
    }

    @Test
    @DisplayName("이벤트마다 버전 번호가 가장 큰 버전의 HTML 만 한 번에 가져온다")
    void 최신_버전의_HTML만_가져온다() {
        Map<Long, String> result = htmls(withThreeVersions, withOneVersion);

        assertEquals(2, result.size());
        assertEquals("<p>v3 최신</p>", result.get(withThreeVersions));
        assertEquals("<p>only</p>", result.get(withOneVersion));
    }

    @Test
    @DisplayName("버전이 없는 이벤트는 결과에 없다")
    void 버전이_없으면_결과에_없다() {
        Map<Long, String> result = htmls(withThreeVersions, withoutVersion);

        assertEquals(1, result.size());
        assertTrue(result.containsKey(withThreeVersions));
        assertTrue(!result.containsKey(withoutVersion));
    }

    @Test
    @DisplayName("요청하지 않은 이벤트는 가져오지 않는다")
    void 요청한_이벤트만_가져온다() {
        Map<Long, String> result = htmls(withOneVersion);

        assertEquals(1, result.size());
        assertEquals("<p>only</p>", result.get(withOneVersion));
    }
}
