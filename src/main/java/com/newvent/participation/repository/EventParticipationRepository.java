package com.newvent.participation.repository;

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
}
