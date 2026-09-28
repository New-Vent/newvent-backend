package com.newvent.event.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.dto.response.EventVersionDetailResponse;
import com.newvent.event.dto.response.EventVersionListResponse;
import com.newvent.event.dto.response.EventVersionSummaryResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventVersionService {

    private final EventRepository eventRepository;
    private final EventVersionRepository eventVersionRepository;

    public EventVersionListResponse getVersions(Long eventId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));

        List<EventVersionSummaryResponse> versions = eventVersionRepository
                .findByEventIdAndCheckpointTrueOrderByVersionNoDesc(eventId)
                .stream()
                .map(version -> EventVersionSummaryResponse.from(version, event))
                .toList();

        return EventVersionListResponse.builder()
                .eventId(eventId)
                .title(event.getTitle())
                .versions(versions)
                .build();
    }

    public EventVersionDetailResponse getVersion(Long eventId, Long versionId) {
        eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));

        EventVersion version = eventVersionRepository
                .findByIdAndEventIdAndCheckpointTrue(versionId, eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.VERSION_NOT_FOUND));

        return EventVersionDetailResponse.from(version);
    }
}
