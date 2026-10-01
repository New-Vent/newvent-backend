package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventVersionRepository;

/**
 * compose PG 실측 테스트. <b>도커가 떠 있어야 돈다</b> (안 떠 있으면 SQL State 08001).
 *
 * ★ @DataJpaTest 는 @Service 를 안 올린다. JpaVersionStore 를 @Import 로 직접 넣는다.
 * ★ ddl-auto=validate — Flyway 가 만든 스키마와 엔티티가 어긋나면 여기서 죽는다.
 *   그게 이 테스트의 절반이다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaVersionStore.class, com.newvent.common.config.JpaAuditingConfig.class})
class JpaVersionStoreTest {

    @Autowired
    private JpaVersionStore store;

    @Autowired
    private EventVersionRepository versions;

    @Autowired
    private TestEntityManager tem;

    /** admins → events 를 네이티브로 심는다. Event 팩토리를 거치지 않는 게 이 테스트의 관심사다 */
    private Long seedEvent(String name) {
        EntityManager em = tem.getEntityManager();
        Long adminId = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) "
                        + "VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, "admin_" + name)
                .setParameter(2, "x")
                .setParameter(3, "관리자")
                .getSingleResult()).longValue();
        return ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade) "
                        + "VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, adminId)
                .setParameter(2, name)
                .setParameter(3, "NORMAL")
                .getSingleResult()).longValue();
    }

    // ── 저장과 순번 ───────────────────────────────────────────────

    @Test
    @DisplayName("첫 버전은 v1, 다음은 v2 — version_no 가 이벤트 안에서 이어진다")
    void 버전_번호가_이어진다() {
        Long eventId = seedEvent("여름 이벤트");

        VersionStore.Saved v1 = store.save(eventId, "<section>1</section>", null);
        VersionStore.Saved v2 = store.save(eventId, "<section>2</section>", v1.versionId());

        assertEquals(1, v1.versionNo());
        assertEquals(2, v2.versionNo());
        assertNotNull(v1.versionId());
        assertTrue(v2.versionId() > v1.versionId());
    }

    @Test
    @DisplayName("★ 이벤트마다 version_no 가 따로 매겨진다 — 전역 카운터가 아니다")
    void 이벤트별로_번호가_따로() {
        Long a = seedEvent("이벤트 A");
        Long b = seedEvent("이벤트 B");

        store.save(a, "<section>a1</section>", null);
        VersionStore.Saved b1 = store.save(b, "<section>b1</section>", null);

        assertEquals(1, b1.versionNo(), "다른 이벤트의 저장이 번호를 먹었습니다.");
    }

    @Test
    @DisplayName("새 행은 저장 지점이 아니다 — 관리자가 저장을 눌러야 켜진다")
    void 새_버전은_체크포인트가_아니다() {
        Long eventId = seedEvent("여름 이벤트");
        VersionStore.Saved v1 = store.save(eventId, "<section>1</section>", null);

        tem.flush();
        tem.clear();

        EventVersion version = versions.findById(v1.versionId()).orElseThrow();

        assertFalse(version.isCheckpoint(), "새로 자동 저장된 버전은 저장 지점이 아니어야 합니다.");
        assertNull(version.getCheckpointedAt(), "저장 지점 지정 시각은 없어야 합니다.");
    }

    // ── sourceVersion ────────────────────────────────────────────

    @Test
    @DisplayName("★ 생성은 sourceVersion 이 null, 수정은 기준이 된 버전을 가리킨다")
    void sourceVersion_이_기준을_가리킨다() {
        Long eventId = seedEvent("여름 이벤트");

        VersionStore.Saved v1 = store.save(eventId, "<section>원본</section>", null);
        VersionStore.Saved v2 = store.save(eventId, "<section>고침</section>", v1.versionId());

        tem.flush();
        tem.clear();

        EventVersion first = versions.findById(v1.versionId()).orElseThrow();
        EventVersion second = versions.findById(v2.versionId()).orElseThrow();

        assertNull(first.getSourceVersion(), "생성 버전에 원본이 붙었습니다.");
        assertNotNull(second.getSourceVersion(), "수정 버전에 원본이 없습니다.");
        assertEquals(v1.versionId(), second.getSourceVersion().getId());
    }

    @Test
    @DisplayName("★ 최신이 아닌 버전을 기준으로 수정해도 된다 — 버전 트리에 가지가 생긴다")
    void 옛_버전을_기준으로_수정할_수_있다() {
        Long eventId = seedEvent("여름 이벤트");

        VersionStore.Saved v1 = store.save(eventId, "<section>1</section>", null);
        store.save(eventId, "<section>2</section>", v1.versionId());
        store.save(eventId, "<section>3</section>", null);

        // 관리자가 이력에서 v1 을 골라 그 기준으로 수정했다
        VersionStore.Saved v4 = store.save(eventId, "<section>1을 고침</section>", v1.versionId());

        tem.flush();
        tem.clear();

        assertEquals(4, v4.versionNo(), "version_no 는 최신 다음이어야 합니다.");
        assertEquals(v1.versionId(),
                versions.findById(v4.versionId()).orElseThrow().getSourceVersion().getId());
    }

    // ── 조회 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("latest 는 마지막 버전의 id·번호·HTML 을 한 번에 준다")
    void 최신_버전의_신분과_내용을_함께_준다() {
        Long eventId = seedEvent("여름 이벤트");

        store.save(eventId, "<section>예전</section>", null);
        VersionStore.Saved v2 = store.save(eventId, "<section>새것</section>", null);

        VersionStore.Snapshot latest = store.latest(eventId).orElseThrow();

        assertEquals(v2.versionId(), latest.versionId());
        assertEquals(2, latest.versionNo());
        assertEquals("<section>새것</section>", latest.html());
    }

    @Test
    @DisplayName("★ latest 의 id 와 HTML 은 같은 행에서 나온다 — 짝이 어긋나지 않는다")
    void 최신_버전의_id와_html이_같은_행에서_나온다() {
        Long eventId = seedEvent("여름 이벤트");

        store.save(eventId, "<section>v1</section>", null);
        store.save(eventId, "<section>v2</section>", null);
        VersionStore.Saved v3 = store.save(eventId, "<section>v3</section>", null);

        VersionStore.Snapshot latest = store.latest(eventId).orElseThrow();

        // id 로 다시 꺼낸 HTML 이 latest 가 준 HTML 과 같아야 한다.
        // 예전처럼 latest(html) 와 latestVersion(id) 를 따로 부르면
        // 그 사이에 새 버전이 저장될 때 이 단정이 깨진다.
        assertEquals(latest.html(),
                store.htmlOf(eventId, latest.versionId()).orElseThrow());
        assertEquals(v3.versionId(), latest.versionId());
    }

    @Test
    @DisplayName("htmlOf 는 그 버전의 HTML 을 준다 — 최신이 아니어도")
    void 특정_버전의_HTML_을_읽는다() {
        Long eventId = seedEvent("여름 이벤트");
        VersionStore.Saved v1 = store.save(eventId, "<section>옛것</section>", null);
        store.save(eventId, "<section>새것</section>", null);

        assertEquals("<section>옛것</section>", store.htmlOf(eventId, v1.versionId()).orElseThrow());
    }

    @Test
    @DisplayName("★ htmlOf 는 남의 이벤트 버전을 주지 않는다")
    void 남의_이벤트_버전은_안_준다() {
        Long a = seedEvent("이벤트 A");
        Long b = seedEvent("이벤트 B");
        VersionStore.Saved ofA = store.save(a, "<section>A 의 것</section>", null);

        assertTrue(store.htmlOf(b, ofA.versionId()).isEmpty(),
                "다른 이벤트의 버전 id 로 HTML 이 나왔습니다.");
        assertTrue(store.htmlOf(a, null).isEmpty());
        assertTrue(store.htmlOf(a, 999_999L).isEmpty());
    }

    @Test
    @DisplayName("버전이 없는 이벤트는 빈 값 — 예외가 아니다")
    void 버전이_없으면_빈_값() {
        Long eventId = seedEvent("아직 안 만든 이벤트");

        assertTrue(store.latest(eventId).isEmpty());
    }

    // ── 서버 재시작을 넘는가 (이 구현의 존재 이유) ─────────────────

    @Test
    @DisplayName("★ 저장한 HTML 이 영속성 컨텍스트를 비워도 살아 있다 — 인메모리와의 차이")
    void 컨텍스트를_비워도_남는다() {
        Long eventId = seedEvent("여름 이벤트");
        store.save(eventId, "<section data-block=\"hero\"><h1>제목</h1></section>", null);

        tem.flush();
        tem.clear();

        assertEquals("<section data-block=\"hero\"><h1>제목</h1></section>",
                store.latest(eventId).orElseThrow().html());
    }

    // ── 유니크 제약 ───────────────────────────────────────────────

    @Test
    @DisplayName("★ 같은 (event_id, version_no) 를 두 번 넣으면 DB 가 막는다")
    void 같은_번호는_두_번_못_넣는다() {
        Long eventId = seedEvent("여름 이벤트");
        store.save(eventId, "<section>1</section>", null);

        // version_no 를 직접 1 로 박아 넣는다 — max+1 을 우회한 경합 상황
        EventVersion clash = EventVersion.create(
                tem.getEntityManager().getReference(Event.class, eventId),
                1, "<section>충돌</section>", null);

        assertThrows(DataIntegrityViolationException.class, () -> {
            versions.save(clash);
            tem.flush();
        }, "uk_event_versions_event_version_no 가 막지 않았습니다. "
                + "JpaVersionStore 의 max+1 은 이벤트당 직렬화에 의존합니다.");
    }

    @Test
    @DisplayName("남의 이벤트 버전을 원본으로 주면 거부한다")
    void 다른_이벤트의_버전은_원본이_될_수_없다() {
        Long a = seedEvent("이벤트 A");
        Long b = seedEvent("이벤트 B");

        Long aVersion = store.save(a, "<p>A 의 버전</p>", null).versionId();

        EventException e = assertThrows(EventException.class,
                () -> store.save(b, "<p>B 의 버전</p>", aVersion));
        assertEquals(EventErrorCode.VERSION_NOT_FOUND, e.getErrorCode());

        // ★ 거부만 보면 부족하다. 행이 안 생겼는지도 본다
        assertTrue(versions.findTopByEventIdOrderByVersionNoDesc(b).isEmpty(),
                "거부된 저장이 행을 남기면 안 된다");
    }
}
