package com.newvent.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.rag.dto.response.IndexStatusResponse;
import com.newvent.rag.repository.RagChunkRepository;

/** RagChunkRepositoryTest와 같은 실PG 패턴. 임베딩은 Mock(결정적·키 불필요). */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmbeddingServiceTest {

    @Autowired
    private EventVersionRepository versions;

    @Autowired
    private RagChunkRepository chunks;

    @Autowired
    private TestEntityManager tem;

    private EmbeddingService indexing() {
        return new EmbeddingService(versions, chunks,
                new RagChunkingService(), new MockEmbeddingClient());
    }

    private static final String HTML = """
            <div class="ev-container event-page">
              <section data-block="hero"><h1>제목</h1></section>
              <section data-block="benefits"><ul><li>쿠폰</li></ul></section>
            </div>
            """;

    private Long seedVersion(String html) {
        EntityManager em = tem.getEntityManager();
        Long adminId = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES (?1,'x','관리자') RETURNING id")
                .setParameter(1, "admin-" + UUID.randomUUID())
                .getSingleResult()).longValue();
        Long eventId = ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade) VALUES (?1,?2,'NORMAL') RETURNING id")
                .setParameter(1, adminId)
                .setParameter(2, "색인-" + UUID.randomUUID())
                .getSingleResult()).longValue();
        return ((Number) em.createNativeQuery(
                        "INSERT INTO event_versions (event_id, version_no, html_content) VALUES (?1,1,?2) RETURNING id")
                .setParameter(1, eventId)
                .setParameter(2, html)
                .getSingleResult()).longValue();
    }

    private Long eventOf(Long versionId) {
        return tem.getEntityManager().createQuery(
                        "SELECT v.event.id FROM EventVersion v WHERE v.id = :id", Long.class)
                .setParameter("id", versionId).getSingleResult();
    }

    @Test
    @DisplayName("색인하면 블록 수만큼 저장되고 모델명이 찍힌다")
    void 색인_저장() {
        Long versionId = seedVersion(HTML);
        Long eventId = eventOf(versionId);
        int n = indexing().index(eventId, versionId);
        assertEquals(2, n);
        assertEquals(2, chunks.countChunks(eventId));
        assertEquals("mock", chunks.findAll().get(0).getEmbeddingModel());
    }

    @Test
    @DisplayName("다시 색인하면 갈아끼워진다 (개수 그대로)")
    void 재색인_교체() {
        Long versionId = seedVersion(HTML);
        Long eventId = eventOf(versionId);
        indexing().index(eventId, versionId);
        indexing().index(eventId, versionId);
        assertEquals(2, chunks.countChunks(eventId));
    }

    @Test
    @DisplayName("reindex에 versionId가 없으면 이벤트 전체를 돌린다")
    void 일괄_재색인() {
        Long versionId = seedVersion(HTML);
        Long eventId = eventOf(versionId);
        int n = indexing().reindex(eventId, null);
        assertEquals(2, n);
    }

    @Test
    @DisplayName("현황은 전체·완료·미완료를 가른다")
    void 현황() {
        Long versionId = seedVersion(HTML);
        Long eventId = eventOf(versionId);
        indexing().index(eventId, versionId);
        IndexStatusResponse status = indexing().getStatus(eventId);
        assertEquals(1, status.totalVersions());
        assertEquals(1, status.indexedVersions());
        assertEquals(0, status.pendingVersions());
        assertEquals(2, status.chunkCount());
        assertNotNull(status.lastIndexedAt());
    }

    @Test
    @DisplayName("없는 버전은 404")
    void 없는_버전() {
        assertThrows(EventException.class,
                () -> indexing().index(-1L, -1L));
    }
}
