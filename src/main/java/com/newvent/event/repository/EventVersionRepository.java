package com.newvent.event.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.event.domain.EventVersion;

public interface EventVersionRepository extends JpaRepository<EventVersion, Long> {

    @EntityGraph(attributePaths = {"requestMessage", "sourceVersion"})
    List<EventVersion> findByEventIdAndCheckpointTrueOrderByVersionNoDesc(Long eventId);
}
