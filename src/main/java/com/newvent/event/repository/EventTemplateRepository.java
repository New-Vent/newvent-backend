package com.newvent.event.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.event.domain.EventTemplate;

public interface EventTemplateRepository extends JpaRepository<EventTemplate, Long> {

    @Query("SELECT t FROM EventTemplate t WHERE t.active = true ORDER BY t.id ASC")
    List<EventTemplate> findAllActive();

    @Query("SELECT t FROM EventTemplate t WHERE t.code = :templateKey")
    Optional<EventTemplate> findByKey(@Param("templateKey") String templateKey);
}
