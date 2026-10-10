package com.newvent.participation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.newvent.admin.domain.Admin;
import com.newvent.event.domain.Event;
import com.newvent.event.repository.EventRepository;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.domain.Game;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;

@DataJpaTest
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
    DelayedEventDrawService.class,
    DelayedEventDrawIntegrationTest.FixedClockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DelayedEventDrawIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-15T14:00:00+09:00");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DelayedEventDrawService service;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventParticipationRepository participationRepository;

    private TransactionTemplate transaction;
    private Long eventId;
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            List<Admin> admins = entityManager.createQuery(
                "SELECT a FROM Admin a ORDER BY a.id",
                Admin.class
            ).setMaxResults(1).getResultList();

            assertThat(admins)
                .as("테스트 DB에 관리자 데이터가 하나 이상 필요합니다.")
                .isNotEmpty();

            Game basic = entityManager.createQuery(
                "SELECT g FROM Game g WHERE g.code = :code",
                Game.class
            ).setParameter("code", "BASIC").getSingleResult();

            Event event = Event.createDraft(
                admins.getFirst(),
                null,
                "자동 추첨 통합 테스트",
                NOW.minusDays(2),
                NOW.minusDays(1),
                MembershipGrade.NORMAL
            );

            // 종료 이벤트도 추첨해야 한다.
            event.end();

            ReflectionTestUtils.setField(event, "createdAt", NOW.minusDays(2));
            ReflectionTestUtils.setField(event, "updatedAt", NOW.minusDays(2));

            entityManager.persist(event);
            entityManager.flush();
            eventId = event.getId();

            // 현재 엔티티에 생성 메서드가 없어 테스트에서만 필드를 설정한다.
            EventGameConfig config =
                org.springframework.beans.BeanUtils.instantiateClass(EventGameConfig.class);

            ReflectionTestUtils.setField(config, "event", event);
            ReflectionTestUtils.setField(config, "game", basic);
            ReflectionTestUtils.setField(config, "config", Map.of(
                "resultMode", "DELAYED",
                "winnerCount", 2,
                "announcementAt", NOW.toString(),
                "prizeName", "커피 쿠폰"
            ));
            ReflectionTestUtils.setField(config, "createdAt", NOW.minusDays(2));

            entityManager.persist(config);

            for (int index = 0; index < 5; index++) {
                String unique = UUID.randomUUID().toString();

                User user = new User(
                    "draw-" + unique,
                    "test-password-hash",
                    "추첨 테스트 회원",
                    unique + "@example.com",
                    "01012345678",
                    50000,
                    MembershipGrade.NORMAL
                );

                ReflectionTestUtils.setField(user, "createdAt", NOW.minusDays(2));
                ReflectionTestUtils.setField(user, "updatedAt", NOW.minusDays(2));

                entityManager.persist(user);
                entityManager.flush();
                userIds.add(user.getId());

                EventParticipation participation = EventParticipation.create(
                    event,
                    user,
                    Map.of(),
                    Map.of("status", "PENDING")
                );

                ReflectionTestUtils.setField(
                    participation,
                    "createdAt",
                    NOW.minusDays(2)
                );

                entityManager.persist(participation);
            }

            entityManager.flush();
        });
    }

    @AfterEach
    void cleanUp() {
        transaction.executeWithoutResult(status -> {
            if (eventId != null) {
                entityManager.createQuery("""
                        DELETE FROM EventParticipation p
                        WHERE p.event.id = :eventId
                        """)
                    .setParameter("eventId", eventId)
                    .executeUpdate();

                entityManager.createQuery("""
                        DELETE FROM EventGameConfig c
                        WHERE c.event.id = :eventId
                        """)
                    .setParameter("eventId", eventId)
                    .executeUpdate();

                entityManager.createQuery("""
                        DELETE FROM Event e
                        WHERE e.id = :eventId
                        """)
                    .setParameter("eventId", eventId)
                    .executeUpdate();
            }

            if (!userIds.isEmpty()) {
                entityManager.createQuery("""
                        DELETE FROM User u
                        WHERE u.id IN :userIds
                        """)
                    .setParameter("userIds", userIds)
                    .executeUpdate();
            }
        });
    }

    @Test
    void 실제_JSON_조회와_추첨_결과_저장이_동작한다() {
        assertThat(service.findCandidateEventIds()).contains(eventId);

        transaction.executeWithoutResult(status -> {
            assertThat(participationRepository.findRandomPendingIds(eventId, 2))
                .hasSize(2);
        });

        service.drawEvent(eventId);

        assertResultCounts(2, 3, 0);

        assertThat(service.findCandidateEventIds()).doesNotContain(eventId);

        List<Map<String, Object>> firstResults = readResults();

        service.drawEvent(eventId);

        assertThat(readResults()).containsExactlyElementsOf(firstResults);
    }

    @Test
    void 같은_이벤트를_동시에_추첨해도_당첨_인원은_유지된다()
        throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        try {
            var first = executor.submit(() ->
                transaction.executeWithoutResult(status -> {
                    // 첫 트랜잭션이 이벤트 잠금을 잡은 상태에서
                    // 두 번째 추첨 실행을 시작한다.
                    eventRepository.findByIdForDraw(eventId)
                        .orElseThrow();

                    lockAcquired.countDown();
                    await(releaseLock);

                    service.drawEvent(eventId);
                })
            );

            assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> {
                secondStarted.countDown();
                service.drawEvent(eventId);
            });

            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            releaseLock.countDown();

            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);

            assertResultCounts(2, 3, 0);
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void 결과를_DB에_flush한_뒤_실패해도_전체_롤백된다() {
        assertThatThrownBy(() ->
            transaction.executeWithoutResult(status -> {
                service.drawEvent(eventId);

                // SQL UPDATE까지 실행한 다음 실패를 발생시킨다.
                entityManager.flush();

                throw new IllegalStateException("강제 실패");
            })
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("강제 실패");

        assertResultCounts(0, 0, 5);

        // 롤백된 이벤트는 다시 정상 처리할 수 있다.
        service.drawEvent(eventId);

        assertResultCounts(2, 3, 0);
    }

    @Test
    void 미당첨_갱신은_한_번에_요청한_개수까지만_처리한다() {
        transaction.executeWithoutResult(status -> {
            eventRepository.findByIdForDraw(eventId).orElseThrow();

            assertThat(participationRepository.updatePendingAsLostInBatch(eventId, 2))
                .isEqualTo(2);
            assertThat(participationRepository.updatePendingAsLostInBatch(eventId, 2))
                .isEqualTo(2);
            assertThat(participationRepository.updatePendingAsLostInBatch(eventId, 2))
                .isEqualTo(1);
            assertThat(participationRepository.updatePendingAsLostInBatch(eventId, 2))
                .isZero();
        });

        assertResultCounts(0, 5, 0);
    }

    @Test
    void 여러_묶음을_처리해도_전체_롤백과_재실행이_동작한다() {
        // 기존 5명 + 추가 2,000명 = 2,005명
        // 당첨 1,001명, 미당첨 1,004명으로 양쪽 모두 배치 경계를 넘는다.
        prepareLargeDraw(2_000, 1_001);

        assertThatThrownBy(() ->
            transaction.executeWithoutResult(status -> {
                service.drawEvent(eventId);
                throw new IllegalStateException("강제 실패");
            })
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("강제 실패");

        assertResultCounts(0, 0, 2_005);

        service.drawEvent(eventId);

        assertResultCounts(1_001, 1_004, 0);

        List<Map<String, Object>> firstResults = readResults();

        service.drawEvent(eventId);

        assertThat(readResults()).containsExactlyElementsOf(firstResults);
    }

    private void prepareLargeDraw(int additionalCount, int winnerCount) {
        transaction.executeWithoutResult(status -> {
            Event event = entityManager.find(Event.class, eventId);

            EventGameConfig config = entityManager.createQuery("""
                SELECT c
                FROM EventGameConfig c
                WHERE c.event.id = :eventId
                """, EventGameConfig.class)
                .setParameter("eventId", eventId)
                .getSingleResult();

            ReflectionTestUtils.setField(config, "config", Map.of(
                "resultMode", "DELAYED",
                "winnerCount", winnerCount,
                "announcementAt", NOW.toString(),
                "prizeName", "커피 쿠폰"
            ));

            List<User> additionalUsers = new ArrayList<>();

            for (int index = 0; index < additionalCount; index++) {
                String unique = UUID.randomUUID().toString();

                User user = new User(
                    "draw-" + unique,
                    "test-password-hash",
                    "추첨 테스트 회원",
                    unique + "@example.com",
                    "01012345678",
                    50000,
                    MembershipGrade.NORMAL
                );

                ReflectionTestUtils.setField(user, "createdAt", NOW.minusDays(2));
                ReflectionTestUtils.setField(user, "updatedAt", NOW.minusDays(2));

                entityManager.persist(user);
                additionalUsers.add(user);

                EventParticipation participation = EventParticipation.create(
                    event,
                    user,
                    Map.of(),
                    Map.of("status", "PENDING")
                );

                ReflectionTestUtils.setField(
                    participation,
                    "createdAt",
                    NOW.minusDays(2)
                );

                entityManager.persist(participation);
            }

            entityManager.flush();

            // 기존 cleanUp()에서 추가 회원도 삭제하도록 ID를 기록한다.
            for (User user : additionalUsers) {
                userIds.add(user.getId());
            }
        });
    }


    private List<Map<String, Object>> readResults() {
        return transaction.execute(status ->
            entityManager.createQuery("""
                        SELECT p
                        FROM EventParticipation p
                        WHERE p.event.id = :eventId
                        ORDER BY p.id
                        """, EventParticipation.class)
                .setParameter("eventId", eventId)
                .getResultList()
                .stream()
                .map(p -> Map.copyOf(p.getResultData()))
                .toList()
        );
    }

    private void assertResultCounts(long won, long lost, long pending) {
        List<Map<String, Object>> results = readResults();

        assertThat(results).hasSize(Math.toIntExact(won + lost + pending));

        assertThat(countStatus(results, "WON")).isEqualTo(won);
        assertThat(countStatus(results, "LOST")).isEqualTo(lost);
        assertThat(countStatus(results, "PENDING")).isEqualTo(pending);

        for (Map<String, Object> result : results) {
            if ("WON".equals(result.get("status"))) {
                assertThat(result).isEqualTo(Map.of(
                    "status", "WON",
                    "prizeName", "커피 쿠폰"
                ));
            } else if ("LOST".equals(result.get("status"))) {
                assertThat(result).isEqualTo(Map.of("status", "LOST"));
            }
        }
    }

    private long countStatus(
        List<Map<String, Object>> results,
        String status
    ) {
        return results.stream()
            .filter(result -> status.equals(result.get("status")))
            .count();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 실행 대기 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 실행 대기 중 중단", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock delayedDrawTestClock() {
            return Clock.fixed(NOW.toInstant(), NOW.getOffset());
        }
    }
}
