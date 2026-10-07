package com.newvent.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.rag.domain.RagChunk;
import com.newvent.rag.dto.response.VersionSimilarityResponse;
import com.newvent.rag.repository.RagChunkRepository;

/** 유사 버전 탐색. 벡터는 직접 박는다 — impl 이 임베딩을 새로 부르지 않는 것도 함께 검증한다. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VersionCompareServiceTest {

    static class FixedModel implements EmbeddingClient {
        @Override public float[] embed(String text) {
            throw new AssertionError("유사 버전 탐색은 임베딩을 새로 부르지 않는다");
        }
        @Override public int dimension() { return 1024; }
        @Override public String modelName() { return "test-similar"; }
    }

    private static final Instant AT = Instant.parse("2026-09-21T03:00:00Z");

    @Autowired
    private RagChunkRepository chunks;

    @Autowired
    private EventVersionRepository versions;

    @Autowired
    private TestEntityManager tem;

    private VersionCompareService compare() {
        return new VersionCompareServiceImpl(versions, chunks, new FixedModel());
    }

    private Event seedEvent(String title) {
        EntityManager em = tem.getEntityManager();
        Long adminId = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES (?1,'x','관리자') RETURNING id")
                .setParameter(1, "admin-" + UUID.randomUUID())
                .getSingleResult()).longValue();
        Long eventId = ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade) VALUES (?1,?2,'NORMAL') RETURNING id")
                .setParameter(1, adminId)
                .setParameter(2, title)
                .getSingleResult()).longValue();
        return em.getReference(Event.class, eventId);
    }

    private Long seedVersion(Long eventId, int versionNo) {
        return ((Number) tem.getEntityManager().createNativeQuery(
                        "INSERT INTO event_versions (event_id, version_no, html_content) VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, eventId)
                .setParameter(2, versionNo)
                .setParameter(3, "<section>seed</section>")
                .getSingleResult()).longValue();
    }

    private static float[] vec(double... first) {
        float[] v = new float[1024];
        for (int i = 0; i < first.length; i++) {
            v[i] = (float) first[i];
        }
        return v;
    }

    private void seedChunk(Event event, Long versionId, String blockKey, String content, float[] vector) {
        EventVersion version = tem.getEntityManager().getReference(EventVersion.class, versionId);
        chunks.save(RagChunk.create(event, version, blockKey, 0, content,
                "test-similar", vector, AT));
    }

    @Test
    @DisplayName("유사도 내림차순으로 돌려주고 기준 버전 자신은 제외한다")
    void orderBySimilarityDescAndExcludesSelf() {
        Event event = seedEvent("유사버전");
        Long v1 = seedVersion(event.getId(), 1);
        Long v2 = seedVersion(event.getId(), 2);
        Long v3 = seedVersion(event.getId(), 3);
        // v1 기준 벡터 e0. v2 동일 → 유사도 1.0. v3 cosine 0.8 → 유사도 0.8
        seedChunk(event, v1, "hero", "기준", vec(1.0));
        seedChunk(event, v2, "hero", "거의 같음", vec(1.0));
        seedChunk(event, v3, "hero", "조금 다름", vec(0.8, 0.6));

        List<VersionSimilarityResponse> out = compare().similarVersions(event.getId(), v1, 5);

        assertEquals(2, out.size());
        assertEquals(v2, out.get(0).versionId());
        assertEquals(1.0, out.get(0).similarity(), 1e-9);
        assertEquals(2, out.get(0).versionNo());
        assertEquals(v3, out.get(1).versionId());
        // ★ float→DB 문자열→pgvector round-trip 오차가 ~1e-8 나므로 허용오차는 1e-6으로 둔다
        assertEquals(0.8, out.get(1).similarity(), 1e-6);
        assertTrue(out.stream().noneMatch(r -> r.versionId().equals(v1)),
                "기준 버전 자신이 결과에 들어갔다");
        assertEquals(1, out.get(0).topChunks().size());
    }

    @Test
    @DisplayName("없는 버전이면 404다")
    void missingVersionIs404() {
        Event event = seedEvent("없음");

        EventException ex = assertThrows(EventException.class,
                () -> compare().similarVersions(event.getId(), 999999L, 5));
        assertEquals("EVENT404-3", ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("기준 버전이 미색인이면 빈 목록이다")
    void unindexedVersionReturnsEmpty() {
        Event event = seedEvent("미색인");
        Long v1 = seedVersion(event.getId(), 1);
        Long v2 = seedVersion(event.getId(), 2);
        seedChunk(event, v2, "hero", "v2만 색인", vec(1.0));

        List<VersionSimilarityResponse> out = compare().similarVersions(event.getId(), v1, 5);

        assertTrue(out.isEmpty());
    }
}
