package com.newvent.event.repository;

import java.util.List;
import java.util.Optional;

import com.newvent.event.domain.Event;

public interface EventRepository {
    List<Event> findAll();

    Optional<Event> findById(Long id);

    /** id 가 null 이면 새 id 를 발급해 저장한다. */
    Event save(Event event);
}
