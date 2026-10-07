package com.newvent.participation.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.newvent.event.domain.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.participation.domain.EventParticipation;

public interface EventParticipationRepository extends JpaRepository<EventParticipation, Long> {

    // 이벤트당 사용자 1회 참여 제한을 위한 중복 참여 확인
    boolean existsByEventIdAndUserId(Long eventId, Long userId);

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

    // 반드시 이벤트 잠금을 획득한 뒤 호출
    @Query("""
        SELECT p
        FROM EventParticipation p
        WHERE p.event.id = :eventId
          AND function(
              'jsonb_extract_path_text',
              p.resultData,
              'status'
          ) = 'PENDING'
        ORDER BY p.id
        """)
    List<EventParticipation> findPendingByEventId(
        @Param("eventId") Long eventId
    );
}
