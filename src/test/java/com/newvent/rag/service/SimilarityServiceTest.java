package com.newvent.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.newvent.rag.domain.RagChunk;
import com.newvent.rag.domain.Vectors;
import com.newvent.rag.dto.response.PromptCandidate;
import com.newvent.rag.dto.response.SearchPreviewResponse;
import com.newvent.rag.repository.RagChunkRepository;

/** Mock은 해시 랜덤이라 순서 재현이 안 된다. 순서를 보는 테스트는 one-hot 스텁으로 한다. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SimilarityServiceTest {

    static class OneHot implements EmbeddingClient {
        private final float[] v = new float[1024];
        OneHot(int hot) { v[hot] = 1.0f; }
        @Override public float[] embed(String text) { return v.clone(); }
        @Override public int dimension() { return 1024; }
        @Override public String modelName() { return "test-onehot"; }
    }

    private static final Instant AT = Instant.parse("2026-09-21T03:00:00Z");

    @Autowired
    private RagChunkRepository chunks;

    @Autowired
    private TestEntityManager tem;

    private SimilarityService search() {
        return new SimilarityService(new OneHot(0), chunks);
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

    private static float[] vec(int hot) {
        float[] v = new float[1024];
        v[hot] = 1.0f;
        return v;
    }

    @Test
    @DisplayName("가까운 것만 오고, 자기 이벤트는 빠진다")
    void 검색_배선() {
        Event mine = seedEvent("내 이벤트");
        Event other = seedEvent("남의 이벤트");
        chunks.save(RagChunk.create(mine, null, "benefits", 0, "내 글", "test-onehot", vec(0), AT));
        chunks.save(RagChunk.create(other, null, "benefits", 0, "남의 가까운 글", "test-onehot", vec(0), AT));
        chunks.save(RagChunk.create(other, null, "hero", 0, "남의 먼 글", "test-onehot", vec(1), AT));

        List<RagChunk> hits = search().search(mine.getId(), "쿠폰", 3);
        assertEquals(1, hits.size());
        assertEquals("남의 가까운 글", hits.get(0).getContent());
    }

    @Test
    @DisplayName("미리보기는 거리를 함께 준다")
    void 미리보기() {
        Event mine = seedEvent("내 이벤트");
        Event other = seedEvent("남의 이벤트");
        chunks.save(RagChunk.create(other, null, "benefits", 0, "남의 글", "test-onehot", vec(0), AT));

        SearchPreviewResponse res = search().searchPreview(mine.getId(), "쿠폰", 3);
        assertEquals(1, res.results().size());
        assertTrue(res.results().get(0).distance() < 0.01);
    }

    @Test
    @DisplayName("프롬프트 추천은 초안 텍스트를 붙여준다")
    void 프롬프트_추천() {
        Event mine = seedEvent("내 이벤트");
        Event other = seedEvent("남의 이벤트");
        chunks.save(RagChunk.create(other, null, "benefits", 0, "쿠폰 내용", "test-onehot", vec(0), AT));

        List<PromptCandidate> out = search().recommendPrompts(mine.getId(), "쿠폰", 3);
        assertEquals(1, out.size());
        assertEquals("남의 이벤트", out.get(0).eventTitle());
        assertEquals("benefits", out.get(0).blockKey());
        assertTrue(out.get(0).prompt().contains("쿠폰 내용"));
    }

    @Test
    @DisplayName("빈 쿼리는 빈 리스트 (임베딩 호출 없음)")
    void 빈_쿼리() {
        assertTrue(search().search(1L, "  ", 3).isEmpty());
    }

    @Test
    @DisplayName("Vectors.cosine 기본값")
    void 코사인() {
        assertEquals(1.0, Vectors.cosine(vec(0), vec(0)), 1e-9);
        assertEquals(0.0, Vectors.cosine(vec(0), vec(1)), 1e-9);
    }
}
