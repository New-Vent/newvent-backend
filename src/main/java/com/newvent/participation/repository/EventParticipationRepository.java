package com.newvent.participation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.participation.domain.EventParticipation;

public interface EventParticipationRepository extends JpaRepository<EventParticipation, Long> {

    boolean existsByEventIdAndUserId(Long eventId, Long userId);
}
