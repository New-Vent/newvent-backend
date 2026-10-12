package com.newvent.event.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
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

    private final Clock clock;

    public EventVersionListResponse getVersions(Long eventId, Long adminId) {
        Event event = findOwnedEvent(eventId, adminId);

        List<EventVersionSummaryResponse> versions = eventVersionRepository
                .findByEventIdOrderByVersionNoDesc(eventId)
                .stream()
                .map(version -> EventVersionSummaryResponse.from(version, event))
                .toList();

        return EventVersionListResponse.builder()
                .eventId(eventId)
                .title(event.getTitle())
                .versions(versions)
                .build();
    }

    @Transactional
    public void markCheckpoint(Long eventId, Long versionId, Long adminId) {
        findOwnedEvent(eventId, adminId);

        EventVersion version = findVersion(eventId, versionId);
        version.markCheckpoint(OffsetDateTime.now(clock));
    }

    @Transactional
    public void unmarkCheckpoint(Long eventId, Long versionId, Long adminId) {
        Event event = findOwnedEvent(eventId, adminId);

        EventVersion version = findVersion(eventId, versionId);

        if (event.getStatus() == EventStatus.PUBLISHED
                && event.getPublishedVersion() != null
                && versionId.equals(event.getPublishedVersion().getId())) {
            throw new EventException(EventErrorCode.PUBLISHED_VERSION_CHECKPOINT_UNMARK_FORBIDDEN);
        }

        version.unmarkCheckpoint();
    }

    public EventVersionDetailResponse getVersion(Long eventId, Long versionId, Long adminId) {
        findOwnedEvent(eventId, adminId);
        EventVersion version = findVersion(eventId, versionId);
        return EventVersionDetailResponse.from(version);
    }

    private EventVersion findVersion(Long eventId, Long versionId) {
        return eventVersionRepository.findByIdAndEventId(versionId, eventId)
            .orElseThrow(() ->
                new EventException(EventErrorCode.VERSION_NOT_FOUND));
    }

    private Event findOwnedEvent(Long eventId, Long adminId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
            .orElseThrow(() ->
                new EventException(EventErrorCode.EVENT_NOT_FOUND));

        if (adminId == null
            || event.getOwnerAdmin() == null
            || !Objects.equals(event.getOwnerAdmin().getId(), adminId)) {
            throw new EventException(EventErrorCode.EVENT_NOT_ACCESSIBLE);
        }

        return event;
    }
}
