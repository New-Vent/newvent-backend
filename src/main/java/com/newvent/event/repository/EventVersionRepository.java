package com.newvent.event.repository;

import com.newvent.event.domain.EventVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventVersionRepository extends JpaRepository<EventVersion, Long> {

    @EntityGraph(attributePaths = {"requestMessage", "sourceVersion"})
    List<EventVersion> findByEventIdAndCheckpointTrueOrderByVersionNoDesc(Long eventId);
}
