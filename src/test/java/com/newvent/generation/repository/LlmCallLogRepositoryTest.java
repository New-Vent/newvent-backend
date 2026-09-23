package com.newvent.generation.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.dao.DataIntegrityViolationException;

import com.newvent.event.domain.Event;
import com.newvent.generation.domain.FailureType;
import com.newvent.generation.domain.LlmCallLog;

// compose PG 실측 테스트
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LlmCallLogRepositoryTest {

    @Autowired
    private LlmCallLogRepository repo;

    @Autowired
    private TestEntityManager tem;

    // KST 자정(2026-09-21 00:00)과 그다음 날 자정
    private static final Instant KST_MIDNIGHT = Instant.parse("2026-09-20T15:00:00Z");
    private static final Instant NEXT_KST_MIDNIGHT = Instant.parse("2026-09-21T15:00:00Z");

    private Event seedEvent() {
        EntityManager em = tem.getEntityManager();
        Long adminId = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, "admin1")
                .setParameter(2, "x")
                .setParameter(3, "관리자")
                .getSingleResult()).longValue();
        Long eventId = ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade) VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, adminId)
                .setParameter(2, "여름 이벤트")
                .setParameter(3, "NORMAL")
                .getSingleResult()).longValue();
        return em.getReference(Event.class, eventId);
    }

    private static LlmCallLog row(Event event, UUID req, int attempt, Instant at) {
        return LlmCallLog.create(event, null, req, attempt, "qwen2.5:7b", "mock",
                100, 10, 20, false, true, true, null, null, at);
    }

    @Test
    @DisplayName("구간 카운트는 start 포함·end 제외 - 다음 날 00:00 행은 제외된다")
    void 구간_카운트_경계_확인() {
        Event event = seedEvent();
        repo.save(row(event, UUID.randomUUID(), 1, KST_MIDNIGHT));      // 00:00:00 → 오늘 포함
        repo.save(row(event, UUID.randomUUID(), 1, NEXT_KST_MIDNIGHT)); // 다음 날 00:00:00 → 오늘 아님

        long today = repo.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                KST_MIDNIGHT, NEXT_KST_MIDNIGHT);

        assertEquals(1L, today,
                "내일 00:00:00 행이 오늘 집계에 포함되면 안 됩니다. 섞이면 일일 사용량이 어긋나 상한 체크가 틀어집니다.");
    }

    @Test
    @DisplayName("주간 합계는 KST 월~일 기준, 모델별로 묶인다")
    void 주간_합계_모델별_묶음() {
        Event event = seedEvent();
        UUID req = UUID.randomUUID();
        // 같은 주(2026-09-21 월요일 시작) 모델 두 개. 같은 req·다른 attempt = 재시도 묶음 재현
        repo.save(LlmCallLog.create(event, null, req, 1, "model-a", "mock",
                100, 10, 20, false, true, true, null, null,
                Instant.parse("2026-09-21T03:00:00Z"))); // 월 12:00 KST
        repo.save(LlmCallLog.create(event, null, req, 2, "model-b", "mock",
                50, 5, 20, false, true, true, null, null,
                Instant.parse("2026-09-21T00:00:00Z"))); // 월 09:00 KST
        // 지난주 행 — from 기준으로 제외되어야 한다 (attempt 1이라 별도 UUID)
        repo.save(LlmCallLog.create(event, null, UUID.randomUUID(), 1, "model-a", "mock",
                999, 999, 20, false, true, true, null, null,
                Instant.parse("2026-09-13T15:00:00Z"))); // 전주 월 09:00 KST

        List<LlmCallLogRepository.WeeklyUsageRow> rows =
                repo.sumTokensByWeek(KST_MIDNIGHT); // 2026-09-21 00:00 KST 부터

        assertEquals(2, rows.size(), "지난주 행이 합계에 섞였습니다.");
        assertEquals("model-a", rows.get(0).getModelName());
        assertEquals(100L, rows.get(0).getInputTokens());
        assertEquals(10L, rows.get(0).getOutputTokens());
        assertEquals("model-b", rows.get(1).getModelName());
    }

    @Test
    @DisplayName("잘림 행이 DDL CHECK(truncated 상태)를 통과한다")
    void 잘림_행_CHECK_통과() {
        Event event = seedEvent();
        LlmCallLog row = LlmCallLog.create(event, null, UUID.randomUUID(), 1,
                "qwen2.5:7b", "mock", 100, 1536, 30,
                true, true, false, // truncated, call_ok=TRUE, valid_ok=FALSE
                FailureType.TRUNCATED, null,
                Instant.parse("2026-09-21T03:00:00Z"));

        LlmCallLog saved = repo.saveAndFlush(row); // CHECK는 flush 시점에 평가

        assertTrue(saved.isTruncated());
        assertTrue(saved.isCallOk());
        assertFalse(saved.isValidOk());
        assertEquals(FailureType.TRUNCATED, saved.getFailureType());
    }

    @Test
    @DisplayName("동일 요청의 같은 회차는 거부된다 - UNIQUE(request_id, attempt_no)")
    void 동일요청_중복회차_거부() {
        Event event = seedEvent();
        UUID req = UUID.randomUUID();

        assertThrows(DataIntegrityViolationException.class, () -> {
            repo.save(row(event, req, 1, KST_MIDNIGHT));
            repo.save(row(event, req, 1, NEXT_KST_MIDNIGHT));
        });
    }
}
