package com.newvent.event.repository;

import java.util.List;
import java.util.Optional;

import com.newvent.event.domain.EventTemplate;

public interface EventTemplateRepository {
    List<EventTemplate> findAllActive();

    Optional<EventTemplate> findByKey(String templateKey);
}
