package com.newvent.event.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.event.exception.EventNotFoundException;
import com.newvent.event.repository.EventRepository;

@Service
public class PublicEventService {

    // REQ-EVT-15: 종료 며칠 전부터 "마감 임박"으로 표시할지는 기획 확정 전이라 3일로 임시 지정.
    private static final int CLOSING_SOON_DAYS = 3;

    private final EventRepository eventRepository;

    public PublicEventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public Event getPublicEvent(Long eventId) {
        Event event = eventRepository.findPublicEventById(eventId, EventStatus.DRAFT)
                .orElseThrow(EventNotFoundException::new);

        if (!isWithinAccessiblePeriod(event, OffsetDateTime.now())) {
            throw new EventNotAccessibleException();
        }

        return event;
    }

    // REQ-PUB-02: 공개 기간(startDate~endDate) 밖이면 존재 자체를 노출하지 않고 접근 불가로 처리.
    private boolean isWithinAccessiblePeriod(Event event, OffsetDateTime now) {
        if (event.getStatus() == EventStatus.ENDED) {
            return false;
        }
        if (event.getStartDate() != null && now.isBefore(event.getStartDate())) {
            return false;
        }
        return event.getEndDate() == null || !now.isAfter(event.getEndDate());
    }

    public boolean isClosingSoon(Event event, OffsetDateTime now) {
        if (event.getEndDate() == null) {
            return false;
        }
        return !now.isAfter(event.getEndDate())
                && !now.plusDays(CLOSING_SOON_DAYS).isBefore(event.getEndDate());
    }
}
