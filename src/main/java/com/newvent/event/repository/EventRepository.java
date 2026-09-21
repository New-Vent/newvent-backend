package com.newvent.event.repository;

import java.util.List;
import java.util.Optional;

import com.newvent.event.domain.Event;

public interface EventRepository {
    List<Event> findAll();
    Optional<Event> findById(Long id);
}
