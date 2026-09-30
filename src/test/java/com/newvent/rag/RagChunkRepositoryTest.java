package com.newvent.rag;

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
import com.newvent.rag.repository.RagChunkRepository;

// compose PG 실측 테스트
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class RagChunkRepositoryTest {

	@Autowired
	private RagChunkRepository repo;

	@Autowired
	private TestEntityManager tem;

	private static final Instant AT = Instant.parse("2026-09-21T03:00:00Z");
	private static final String MODEL = "amazon.titan-embed-text-v2:0";

	private Event seedEvent() {
	    return seedEvent(UUID.randomUUID().toString());
	}

	private Event seedEvent(String title) {
        EntityManager em = tem.getEntityManager();
        Long adminId = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, "admin-" + title)
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

	// index 번째만 1.0 인 1024차원 one-hot 벡터
	private static float[] vec(int hot) {
		float[] v = new float[1024];
		v[hot] = 1.0f;
		return v;
	}

	private RagChunk chunk(Event event, String blockKey, String model, float[] embedding) {
		return RagChunk.create(event, null, blockKey, 0, "내용" + blockKey, model, embedding, AT);
	}

    @Test
    @DisplayName("저장 후 조회하면 값과 벡터가 그대로 돌아온다.")
    void 저장_조회_왕복() {
        RagChunk saved = repo.saveAndFlush(
                chunk(seedEvent(), "benefits", MODEL, vec(0)));
        tem.clear();

        RagChunk found = repo.findById(saved.getId()).orElseThrow();

        assertEquals("benefits", found.getBlockKey());
        assertEquals(MODEL, found.getEmbeddingModel());

        float[] v = Vectors.fromDb(found.getEmbedding());
        assertEquals(1024, v.length);
        assertEquals(1.0f, v[0]);
    }

    @Test
    @DisplayName("가까운 순서대로 오고 maxDistance 밖은 잘린다.")
    void 유사도_순서와_절단() {
        Event event = seedEvent();
        repo.save(chunk(event, "benefits", MODEL, vec(0)));   // 거리 0
        repo.save(chunk(event, "hero", MODEL, vec(1)));       // 거리 1 (직교)

        List<RagChunk> hits = repo.findSimilar(
                Vectors.toDb(vec(0)), MODEL, -1L, 0.4, 3);

        assertEquals(1, hits.size());
        assertEquals("benefits", hits.get(0).getBlockKey());
    }

    @Test
    @DisplayName("다른 모델 벡터는 섞이지 않는다.")
    void 모델_필터() {
        Event event = seedEvent();
        repo.save(chunk(event, "benefits", MODEL, vec(0)));
        repo.save(chunk(event, "benefits", "bge-m3", vec(0)));

        List<RagChunk> hits = repo.findSimilar(
                Vectors.toDb(vec(0)), MODEL, -1L, 0.4, 3);

        assertEquals(1, hits.size());
        assertEquals(MODEL, hits.get(0).getEmbeddingModel());
    }

    @Test
    @DisplayName("자기 이벤트는 제외된다.")
    void 자기_이벤트_제외() {
        Event mine = seedEvent();
        Event other = seedEvent();
        repo.save(chunk(mine, "benefits", MODEL, vec(0)));

        List<RagChunk> hits = repo.findSimilar(
                Vectors.toDb(vec(0)), MODEL, mine.getId(), 0.4, 3);
        assertTrue(hits.isEmpty());

        List<RagChunk> hits2 = repo.findSimilar(
                Vectors.toDb(vec(0)), MODEL, other.getId(), 0.4, 3);
        assertEquals(1, hits2.size());
    }
}
