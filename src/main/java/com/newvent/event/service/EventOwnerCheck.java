package com.newvent.event.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.auth.dto.AuthUser;
import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;

/**
 * 이벤트를 만든 관리자인지 확인한다. 이벤트 서비스 밖(generation · rag 컨트롤러)에서 쓴다.
 *
 * 없거나 휴지통에 있는 이벤트는 404 (EVENT404-0), 남의 이벤트는 403 (COMMON403-0).
 */
@Component
public class EventOwnerCheck {

    private final EventRepository eventRepository;

    public EventOwnerCheck(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public Event requireOwned(Long eventId, AuthUser principal) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        if (principal == null || !principal.admin() || !event.ownedBy(principal.id())) {
            throw new AccessDeniedException("이벤트 소유 관리자만 사용할 수 있습니다.");
        }
        return event;
    }
}
