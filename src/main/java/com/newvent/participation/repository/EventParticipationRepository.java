package com.newvent.participation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.participation.domain.EventParticipation;

public interface EventParticipationRepository extends JpaRepository<EventParticipation, Long> {

    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    // 참여 ID와 사용자 ID를 함께 확인하여 본인의 참여 기록만 조회
    @EntityGraph(attributePaths = "event")
    Optional<EventParticipation> findByIdAndUserId(Long participationId, Long userId);
}
