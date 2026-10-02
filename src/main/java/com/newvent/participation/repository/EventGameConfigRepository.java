package com.newvent.participation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.participation.domain.EventGameConfig;

public interface EventGameConfigRepository extends JpaRepository<EventGameConfig, Long> {

    @EntityGraph(attributePaths = "game")
    List<EventGameConfig> findAllByEventId(Long eventId);
}
