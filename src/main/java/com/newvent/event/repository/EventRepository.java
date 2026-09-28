package com.newvent.event.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.event.domain.Event;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByIdAndDeletedAtIsNull(Long eventId);
}
