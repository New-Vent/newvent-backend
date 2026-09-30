package com.newvent.event.service;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.EventStatus;
import com.newvent.event.repository.EventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventExpirationService {

    private final EventRepository eventRepository;
    private final Clock clock;

    @Transactional
    public int endExpiredEvents() {
        return eventRepository.endExpiredEvents(
                EventStatus.PUBLISHED,
                EventStatus.ENDED,
                OffsetDateTime.now(clock));
    }
}
