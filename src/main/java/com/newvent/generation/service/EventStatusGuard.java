package com.newvent.generation.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.newvent.common.exception.code.ErrorCode;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.repository.EventRepository;
import com.newvent.generation.exception.GenerationErrorCode;

@Component
public class EventStatusGuard implements EventGuard {

    private final EventRepository events;

    public EventStatusGuard(EventRepository events) {
        this.events = events;
    }

    @Override
    public Optional<ErrorCode> rejectReason(Long eventId) {
        // ★ 삭제 필터를 쿼리가 한다. 없는 것과 지워진 것을 구분해서 알려주지 않는다 —
        //   구분하면 "지워진 이벤트가 있었다" 는 사실이 새어 나간다.
        Optional<Event> found = events.findByIdAndDeletedAtIsNull(eventId);

        if (found.isEmpty()) {
            return Optional.of(EventErrorCode.EVENT_NOT_FOUND);
        }

        if (found.get().getStatus() == EventStatus.ENDED) {
            return Optional.of(GenerationErrorCode.EVENT_ENDED);
        }

        return Optional.empty();
    }
}
