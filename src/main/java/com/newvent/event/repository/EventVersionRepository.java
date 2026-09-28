package com.newvent.event.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.event.domain.EventVersion;

public interface EventVersionRepository extends JpaRepository<EventVersion, Long> {

    @EntityGraph(attributePaths = {"requestMessage", "sourceVersion"})
    List<EventVersion> findByEventIdAndCheckpointTrueOrderByVersionNoDesc(Long eventId);

    Optional<EventVersion> findByIdAndEventId(Long versionId, Long eventId);


        /**
         * 이 이벤트의 마지막 버전. version_no 최대값 하나.
         */
    Optional<EventVersion> findTopByEventIdOrderByVersionNoDesc(Long eventId);
}
