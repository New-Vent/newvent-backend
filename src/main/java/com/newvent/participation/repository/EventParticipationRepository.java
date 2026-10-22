package com.newvent.participation.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.event.domain.EventStatus;
import com.newvent.participation.domain.EventParticipation;

public interface EventParticipationRepository extends JpaRepository<EventParticipation, Long> {

    // 이벤트당 사용자 1회 참여 제한을 위한 중복 참여 확인
    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    // 참여 설정을 바꿀 수 있는지 판단할 때 쓴다 — 한 건이라도 있으면 바꿀 수 없다.
    // count 가 아니라 exists 다. 첫 행에서 멈춘다.
    boolean existsByEventId(Long eventId);

    // 참여 ID와 사용자 ID를 함께 확인하여 본인의 참여 기록만 조회
    @EntityGraph(attributePaths = "event")
    Optional<EventParticipation> findByIdAndUserId(Long participationId, Long userId);

    // 본인의 전체 참여 기록을 최신순으로 페이징 조회
    @EntityGraph(attributePaths = "event")
    @Query(
        value = """

            SELECT p
                    FROM EventParticipation p
                    WHERE p.user.id = :userId
                    ORDER BY p.createdAt DESC, p.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM EventParticipation p
                    WHERE p.user.id = :userId
                    """
    )
    Page<EventParticipation> findMyParticipations(
            @Param("userId") Long userId,
            Pageable pageable
    );

    // 본인의 특정 결과 상태 기록을 페이징 조회 — 받은 혜택 목록은 WON 사용
    @EntityGraph(attributePaths = "event")
    @Query(
            value = """
                    SELECT p
                    FROM EventParticipation p
                    WHERE p.user.id = :userId
                      AND function(
                          'jsonb_extract_path_text',
                          p.resultData,
                          'status'
                      ) = :resultStatus
                    ORDER BY p.createdAt DESC, p.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM EventParticipation p
                    WHERE p.user.id = :userId
                      AND function(
                          'jsonb_extract_path_text',
                          p.resultData,
                          'status'
                      ) = :resultStatus
                    """
    )
    Page<EventParticipation> findMyParticipationsByResultStatus(
            @Param("userId") Long userId,
            @Param("resultStatus") String resultStatus,
            Pageable pageable
    );

    /**
     * 한 이벤트의 참여 기록을 최신순으로. <b>관리자가 참여자·당첨자를 볼 때 쓴다.</b>
     *
     * ★ 이 쿼리가 없어서 참여 기록은 DB 에 쌓이기만 하고 아무도 못 봤다.
     *   이 레포지토리의 다른 조회는 전부 {@code user.id} 로 걸려 있다 — 본인 것만 본다.
     *
     * ★ user 를 같이 끌어온다. 목록에 참여자 이름을 띄우는데, 없으면 행마다 조회가 한 번
     *   더 나간다(N+1). 한 페이지가 20행이면 쿼리가 21개다.
     *
     * ★ resultStatus 가 null 이면 전체다. 쿼리를 둘로 쓰지 않는다 — 조건이 한 벌이라
     *   필터를 늘려도 두 곳을 고치지 않는다 (본인 조회는 메서드가 둘로 갈려 있다).
     *
     * ★★ CAST(:resultStatus AS string) 을 지우지 마라 — <b>두 자리 모두 필요하다.</b>
     *   없으면 PostgreSQL 이 {@code ? is null} 의 파라미터 타입을 못 정하고
     *   「알 수 없는 유형 값입니다」(could not determine data type of parameter)로 터진다.
     *   기동 때가 아니라 <b>호출할 때</b> 터지고, 레포지토리를 mock 한 서비스 테스트는
     *   끝까지 통과한다. 그래서 EventParticipationAdminQueryTest 가 실제 PG 로 돌린다.
     *
     *   실측: 첫 자리만 캐스트하면 여전히 같은 오류다. 두 번째 {@code = :resultStatus} 의
     *   null 도 타입이 없어서 그렇다 — {@code jsonb_extract_path_text} 의 반환형으로
     *   추론되지 않는다.
     */
    @EntityGraph(attributePaths = "user")
    @Query(
            value = """
                    SELECT p
                    FROM EventParticipation p
                    WHERE p.event.id = :eventId
                      AND (CAST(:resultStatus AS string) IS NULL
                           OR function(
                               'jsonb_extract_path_text',
                               p.resultData,
                               'status'
                           ) = CAST(:resultStatus AS string))
                    ORDER BY p.createdAt DESC, p.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM EventParticipation p
                    WHERE p.event.id = :eventId
                      AND (CAST(:resultStatus AS string) IS NULL
                           OR function(
                               'jsonb_extract_path_text',
                               p.resultData,
                               'status'
                           ) = CAST(:resultStatus AS string))
                    """
    )
    Page<EventParticipation> findByEventId(
            @Param("eventId") Long eventId,
            @Param("resultStatus") String resultStatus,
            Pageable pageable
    );

    /**
     * 한 이벤트의 참여·당첨·대기 수. <b>필터·페이지와 무관하게</b> 전체를 센다.
     *
     * ★ 목록의 totalElements 로는 이걸 못 만든다 — 그건 필터가 걸린 수다.
     *   「당첨 12명」을 보려고 필터를 걸면 전체 수가 12로 보이면 안 된다.
     */
    @Query(
            value = """
                    SELECT
                        COUNT(*) AS "totalParticipationCount",
                        COUNT(*) FILTER (
                            WHERE p.result_data ->> 'status' = 'WON'
                        ) AS "rewardCount",
                        COUNT(*) FILTER (
                            WHERE p.result_data ->> 'status' = 'PENDING'
                        ) AS "pendingCount"
                    FROM event_participations p
                    WHERE p.event_id = :eventId
                    """,
            nativeQuery = true
    )
    ParticipationSummaryProjection findSummaryByEventId(
            @Param("eventId") Long eventId
    );

    // 목록 필터·페이지와 관계없이 본인의 전체 참여·당첨·대기 수를 한 번에 집계
    @Query(
            value = """
                    SELECT
                        COUNT(*) AS "totalParticipationCount",
                        COUNT(*) FILTER (
                            WHERE p.result_data ->> 'status' = 'WON'
                        ) AS "rewardCount",
                        COUNT(*) FILTER (
                            WHERE p.result_data ->> 'status' = 'PENDING'
                        ) AS "pendingCount"
                    FROM event_participations p
                    WHERE p.user_id = :userId
                    """,
            nativeQuery = true
    )
    ParticipationSummaryProjection findSummaryByUserId(
            @Param("userId") Long userId
    );

    // 마감된 기본형 추후 추첨 이벤트 중 PENDING 참여자가 있는 이벤트를 조회한다.
    // 발표 시각은 서비스에서 읽고 검증한다.
    @Query("""
        SELECT DISTINCT c.event.id
        FROM EventGameConfig c
        WHERE c.game.code = 'BASIC'
          AND function(
              'jsonb_extract_path_text',
              c.config,
              'resultMode'
          ) = 'DELAYED'
          AND c.event.deletedAt IS NULL
          AND c.event.status IN :statuses
          AND c.event.endDate < :now
          AND EXISTS (
              SELECT p.id
              FROM EventParticipation p
              WHERE p.event.id = c.event.id
                AND function(
                    'jsonb_extract_path_text',
                    p.resultData,
                    'status'
                ) = 'PENDING'
          )
        ORDER BY c.event.id
        """)
    List<Long> findDelayedDrawCandidateEventIds(
        @Param("statuses") List<EventStatus> statuses,
        @Param("now") OffsetDateTime now
    );

    // 이벤트 잠금을 획득한 뒤 호출한다.
    // 전체 참여자 엔티티 대신 당첨자로 선정된 ID만 조회한다.
    @Query(
        value = """
        SELECT p.id
        FROM event_participations p
        WHERE p.event_id = :eventId
          AND p.result_data ->> 'status' = 'PENDING'
        ORDER BY random()
        LIMIT :winnerCount
        """,
        nativeQuery = true
    )
    List<Long> findRandomPendingIds(
        @Param("eventId") Long eventId,
        @Param("winnerCount") int winnerCount
    );

    // 선정된 참여자의 결과를 당첨으로 일괄 변경한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE event_participations
        SET result_data = jsonb_build_object(
            'status', 'WON',
            'prizeName', CAST(:prizeName AS text)
        )
        WHERE event_id = :eventId
          AND id IN (:winnerIds)
          AND result_data ->> 'status' = 'PENDING'
        """,
        nativeQuery = true
    )
    int updateWinners(
        @Param("eventId") Long eventId,
        @Param("winnerIds") List<Long> winnerIds,
        @Param("prizeName") String prizeName
    );

    // 남은 대기 참여자를 최대 batchSize명씩 미당첨으로 변경한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        WITH targets AS (
            SELECT p.id
            FROM event_participations p
            WHERE p.event_id = :eventId
              AND p.result_data ->> 'status' = 'PENDING'
            ORDER BY p.id
            LIMIT :batchSize
        )
        UPDATE event_participations p
        SET result_data = jsonb_build_object('status', 'LOST')
        FROM targets t
        WHERE p.id = t.id
          AND p.event_id = :eventId
          AND p.result_data ->> 'status' = 'PENDING'
        """,
        nativeQuery = true
    )
    int updatePendingAsLostInBatch(
        @Param("eventId") Long eventId,
        @Param("batchSize") int batchSize
    );

    // 실제 경기 결과가 등록됐고 아직 발표 처리하지 않은 스포츠 이벤트
    @Query(
        value = """
                SELECT DISTINCT e.id
                FROM events e
                JOIN event_game_configs c ON c.event_id = e.id
                JOIN games g ON g.id = c.game_id
                WHERE g.code = 'SPORTS_PREDICTION'
                  AND e.deleted_at IS NULL
                  AND e.status IN ('PUBLISHED', 'ENDED')
                  AND e.end_date < :now
                  AND c.config ->> 'winningPrediction' IS NOT NULL
                  AND NOT jsonb_exists(c.config, 'drawnAt')
                ORDER BY e.id
                """,
        nativeQuery = true
    )
    List<Long> findSportsDrawCandidateEventIds(
        @Param("now") OffsetDateTime now
    );

    // 혜택 하나의 결과를 최대 batchSize명에게 추가한다.
    // winnersOnly=true: 아직 처리하지 않은 지급 대상자 중 무작위 당첨.
    // winnersOnly=false: 남은 참여자의 혜택 결과 처리.
    //
    // clearAutomatically는 사용하지 않는다.
    // 서비스가 마지막에 gameConfig의 drawnAt을 변경해야 한다.
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
        WITH targets AS (
            SELECT
                p.id,
                (
                    :target = 'ALL_PARTICIPANTS'
                    OR (
                        :target = 'CORRECT_PREDICTIONS'
                        AND p.submitted_data ->> 'prediction'
                            = :winningPrediction
                    )
                ) AS eligible
            FROM event_participations p
            WHERE p.event_id = :eventId
              AND p.result_data ->> 'status' = 'PENDING'
              AND NOT EXISTS (
                  SELECT 1
                  FROM jsonb_array_elements(
                      COALESCE(
                          p.result_data -> 'rewards',
                          '[]'::jsonb
                      )
                  ) AS r(item)
                  WHERE r.item ->> 'rewardId' = :rewardId
              )
              AND (
                  :winnersOnly = false
                  OR (
                      :target = 'ALL_PARTICIPANTS'
                      OR (
                          :target = 'CORRECT_PREDICTIONS'
                          AND p.submitted_data ->> 'prediction'
                              = :winningPrediction
                      )
                  )
              )
            ORDER BY
                CASE
                    WHEN :winnersOnly = true THEN random()
                    ELSE 0
                END,
                p.id
            LIMIT :batchSize
        ),
        results AS (
            SELECT
                t.id,
                (
                    :winnersOnly = true
                    OR (:selection = 'ALL' AND t.eligible)
                ) AS won
            FROM targets t
        )
        UPDATE event_participations p
        SET result_data = jsonb_set(
            p.result_data,
            '{rewards}',
            COALESCE(
                p.result_data -> 'rewards',
                '[]'::jsonb
            ) || jsonb_build_array(
                jsonb_build_object(
                    'rewardId', CAST(:rewardId AS text),
                    'status',
                        CASE WHEN r.won THEN 'WON' ELSE 'LOST' END
                )
                ||
                CASE WHEN r.won
                    THEN jsonb_build_object(
                        'prizeName', CAST(:prizeName AS text)
                    )
                    ELSE '{}'::jsonb
                END
            )
        )
        FROM results r
        WHERE p.id = r.id
          AND p.event_id = :eventId
          AND p.result_data ->> 'status' = 'PENDING'
        """,
        nativeQuery = true
    )
    int appendSportsRewardInBatch(
        @Param("eventId") Long eventId,
        @Param("winningPrediction") String winningPrediction,
        @Param("rewardId") String rewardId,
        @Param("target") String target,
        @Param("selection") String selection,
        @Param("prizeName") String prizeName,
        @Param("winnersOnly") boolean winnersOnly,
        @Param("batchSize") int batchSize
    );

    // 혜택 중 하나라도 WON이면 최상위 상태를 WON으로 확정한다.
    // 기존 목록용 prizeName에는 첫 번째 당첨 경품명을 저장한다.
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
        WITH targets AS (
            SELECT p.id
            FROM event_participations p
            WHERE p.event_id = :eventId
              AND p.result_data ->> 'status' = 'PENDING'
            ORDER BY p.id
            LIMIT :batchSize
        )
        UPDATE event_participations p
        SET result_data =
            jsonb_set(
                p.result_data,
                '{status}',
                to_jsonb(
                    CASE WHEN EXISTS (
                        SELECT 1
                        FROM jsonb_array_elements(
                            p.result_data -> 'rewards'
                        ) AS r(item)
                        WHERE r.item ->> 'status' = 'WON'
                    ) THEN 'WON' ELSE 'LOST' END
                )
            )
            ||
            COALESCE(
                (
                    SELECT jsonb_build_object(
                        'prizeName', r.item ->> 'prizeName'
                    )
                    FROM jsonb_array_elements(
                        p.result_data -> 'rewards'
                    ) WITH ORDINALITY AS r(item, position)
                    WHERE r.item ->> 'status' = 'WON'
                    ORDER BY r.position
                    LIMIT 1
                ),
                '{}'::jsonb
            )
        FROM targets t
        WHERE p.id = t.id
          AND p.event_id = :eventId
          AND p.result_data ->> 'status' = 'PENDING'
        """,
        nativeQuery = true
    )
    int finalizeSportsResultsInBatch(
        @Param("eventId") Long eventId,
        @Param("batchSize") int batchSize
    );

    // 해당 이벤트에서 이미 당첨된 참여자 수
    @Query(
        value = """
        SELECT COUNT(*)
        FROM event_participations
        WHERE event_id = :eventId
          AND result_data ->> 'status' = 'WON'
        """,
        nativeQuery = true
    )
    long countWinnersByEventId(@Param("eventId") Long eventId);
}
