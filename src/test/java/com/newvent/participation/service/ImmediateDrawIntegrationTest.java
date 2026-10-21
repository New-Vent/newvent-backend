package com.newvent.participation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.newvent.common.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.newvent.admin.domain.Admin;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.service.PublicEventService;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.domain.Game;
import com.newvent.participation.dto.response.ParticipationCreateResponse;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.service.UserService;

@DataJpaTest
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
    JpaAuditingConfig.class,
    ParticipationService.class,
    ParticipationValidator.class,
    ParticipationResultProcessor.class,
    ImmediateDrawIntegrationTest.LookupConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ImmediateDrawIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ParticipationService service;

    @Autowired
    private EventParticipationRepository participationRepository;

    @Autowired
    private PublicEventService publicEventService;

    @Autowired
    private UserService userService;

    private TransactionTemplate transaction;
    private Long eventId;
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        reset(publicEventService, userService);
        transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now();

            List<Admin> admins = entityManager.createQuery(
                "SELECT a FROM Admin a ORDER BY a.id",
                Admin.class
            ).setMaxResults(1).getResultList();

            assertThat(admins)
                .as("테스트 DB에 관리자 시드 데이터가 필요합니다.")
                .isNotEmpty();

            Game basic = entityManager.createQuery(
                "SELECT g FROM Game g WHERE g.code = :code",
                Game.class
            ).setParameter("code", "BASIC").getSingleResult();

            Event event = Event.createDraft(
                admins.getFirst(),
                null,
                "즉시 추첨 상한 통합 테스트",
                now.minusDays(1),
                now.plusDays(1),
                MembershipGrade.NORMAL
            );

            ReflectionTestUtils.setField(event, "status", EventStatus.PUBLISHED);
            ReflectionTestUtils.setField(event, "createdAt", now);
            ReflectionTestUtils.setField(event, "updatedAt", now);

            entityManager.persist(event);
            entityManager.flush();
            eventId = event.getId();

            EventGameConfig config = BeanUtils.instantiateClass(EventGameConfig.class);

            ReflectionTestUtils.setField(config, "event", event);
            ReflectionTestUtils.setField(config, "game", basic);
            ReflectionTestUtils.setField(config, "createdAt", now);
            ReflectionTestUtils.setField(config, "config", Map.of(
                "resultMode", "IMMEDIATE",
                "winProbability", 100,
                "prizeName", "커피 쿠폰",
                "maxWinnerCount", 1
            ));

            entityManager.persist(config);

            // 공개 조회 정책은 기존 테스트에서 검증한다.
            // 여기서는 실제 참여 트랜잭션과 DB 잠금을 검증한다.
            when(publicEventService.getPublicEvent(eventId))
                .thenReturn(event);

            for (int index = 0; index < 5; index++) {
                String unique = UUID.randomUUID().toString();

                User user = new User(
                    "instant-" + unique,
                    "test-password-hash",
                    "즉시 추첨 테스트 회원",
                    unique + "@example.com",
                    "01012345678",
                    50000,
                    MembershipGrade.NORMAL
                );

                ReflectionTestUtils.setField(user, "createdAt", now);
                ReflectionTestUtils.setField(user, "updatedAt", now);

                entityManager.persist(user);
                entityManager.flush();

                userIds.add(user.getId());
                when(userService.getById(user.getId())).thenReturn(user);
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
    void 순서대로_참여해도_최대_당첨_인원을_넘지_않는다() {
        changeLimit(2);

        for (Long userId : userIds) {
            service.participate(eventId, userId, null);
        }

        assertResultCounts(2, 3);
    }

    @Test
    void 동시에_참여해도_마지막_경품은_한_명만_당첨된다()
        throws Exception {

        var executor = Executors.newFixedThreadPool(userIds.size());
        CountDownLatch ready = new CountDownLatch(userIds.size());
        CountDownLatch start = new CountDownLatch(1);

        List<Future<ParticipationCreateResponse>> futures = new ArrayList<>();

        try {
            for (Long userId : userIds) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("참여 시작 대기 시간 초과");
                    }

                    // 각 스레드가 서비스 프록시를 호출해 별도 트랜잭션으로 참여한다.
                    return service.participate(eventId, userId, null);
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ParticipationCreateResponse> responses = new ArrayList<>();

            for (Future<ParticipationCreateResponse> future : futures) {
                // 스레드 내부 예외도 테스트 실패로 전달한다.
                responses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(responses).hasSize(5);

            assertThat(responses.stream()
                .filter(response ->
                    "WON".equals(response.resultData().get("status"))
                )
                .count()
            ).isEqualTo(1);

            assertResultCounts(1, 4);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void 참여_저장_후_롤백되면_경품_수량을_차지하지_않는다() {
        assertThatThrownBy(() ->
            transaction.executeWithoutResult(status -> {
                ParticipationCreateResponse response = service.participate(eventId, userIds.getFirst(), null);

                assertThat(response.resultData()).isEqualTo(Map.of(
                    "status", "WON",
                    "prizeName", "커피 쿠폰"
                ));

                entityManager.flush();

                throw new IllegalStateException("강제 롤백");
            })
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("강제 롤백");

        assertResultCounts(0, 0);

        ParticipationCreateResponse response = service.participate(eventId, userIds.get(1), null);

        assertThat(response.resultData()).isEqualTo(Map.of(
            "status", "WON",
            "prizeName", "커피 쿠폰"
        ));

        assertResultCounts(1, 0);
    }

    private void changeLimit(int maxWinnerCount) {
        transaction.executeWithoutResult(status -> {
            EventGameConfig config = entityManager.createQuery("""
                SELECT c
                FROM EventGameConfig c
                WHERE c.event.id = :eventId
                """, EventGameConfig.class)
                .setParameter("eventId", eventId)
                .getSingleResult();

            config.changeConfig(config.getGame(), Map.of(
                "resultMode", "IMMEDIATE",
                "winProbability", 100,
                "prizeName", "커피 쿠폰",
                "maxWinnerCount", maxWinnerCount
            ));

            entityManager.flush();
        });
    }

    private void assertResultCounts(long won, long lost) {
        transaction.executeWithoutResult(status -> {
            List<EventParticipation> participations =
                entityManager.createQuery("""
                    SELECT p
                    FROM EventParticipation p
                    WHERE p.event.id = :eventId
                    """, EventParticipation.class)
                    .setParameter("eventId", eventId)
                    .getResultList();

            assertThat(participations).hasSize(
                Math.toIntExact(won + lost)
            );

            assertThat(participations.stream()
                .filter(p -> "WON".equals(
                    p.getResultData().get("status")
                ))
                .count()
            ).isEqualTo(won);

            assertThat(participations.stream()
                .filter(p -> "LOST".equals(
                    p.getResultData().get("status")
                ))
                .count()
            ).isEqualTo(lost);

            assertThat(
                participationRepository.countWinnersByEventId(eventId)
            ).isEqualTo(won);
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LookupConfig {

        @Bean
        PublicEventService immediateDrawPublicEventService() {
            return mock(PublicEventService.class);
        }

        @Bean
        UserService immediateDrawUserService() {
            return mock(UserService.class);
        }
    }
}
