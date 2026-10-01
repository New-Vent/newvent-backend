package com.newvent.participation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.participation.domain.EventParticipation;

public interface EventParticipationRepository extends JpaRepository<EventParticipation, Long> {

    boolean existsByEventIdAndUserId(Long eventId, Long userId);

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

    long countByUserId(Long userId);

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM event_participations p
                    WHERE p.user_id = :userId
                      AND p.result_data ->> 'status' = :resultStatus
                    """,
            nativeQuery = true
    )
    long countByUserIdAndResultStatus(
            @Param("userId") Long userId,
            @Param("resultStatus") String resultStatus
    );
}
