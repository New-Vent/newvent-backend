package com.newvent.event.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByIdAndDeletedAtIsNull(Long eventId);

    // publishedVersion은 LAZY 연관관계이고 open-in-view: false라, 트랜잭션 안에서 fetch join으로 같이 가져온다.
    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.publishedVersion "
            + "WHERE e.id = :id AND e.deletedAt IS NULL AND e.status <> :excludedStatus")
    Optional<Event> findPublicEventById(@Param("id") Long id, @Param("excludedStatus") EventStatus excludedStatus);
}
