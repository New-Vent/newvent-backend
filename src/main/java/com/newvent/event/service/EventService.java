package com.newvent.event.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.admin.domain.Admin;
import com.newvent.admin.repository.AdminRepository;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventTemplate;
import com.newvent.event.dto.request.EventCreateRequest;
import com.newvent.event.dto.response.EventDetailResponse;
import com.newvent.event.dto.response.EventSummaryResponse;
import com.newvent.event.dto.response.PageResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventTemplateRepository;
import com.newvent.generation.service.GenerationJobStore;

@Service
public class EventService {

    static final Duration CLOSING_SOON_WINDOW = Duration.ofDays(3);

    private final EventRepository eventRepository;
    private final EventTemplateRepository eventTemplateRepository;
    private final AdminRepository adminRepository;
    private final GenerationJobStore generationJobStore;
    private final Clock clock;

    public EventService(
            EventRepository eventRepository,
            EventTemplateRepository eventTemplateRepository,
            AdminRepository adminRepository,
            GenerationJobStore generationJobStore,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.eventTemplateRepository = eventTemplateRepository;
        this.adminRepository = adminRepository;
        this.generationJobStore = generationJobStore;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> findAdminEvents(
            String name,
            EventStatus status,
            OffsetDateTime periodFrom,
            OffsetDateTime periodTo,
            int page,
            int size) {
        if (periodFrom != null && periodTo != null && periodFrom.isAfter(periodTo)) {
            throw new EventException(EventErrorCode.INVALID_SEARCH_PERIOD);
        }

        String namePattern = name == null || name.isBlank()
                ? null
                : "%" + name.trim().toLowerCase(Locale.ROOT) + "%";

        Page<Event> result = eventRepository.findAdminEvents(
                namePattern, status, periodFrom, periodTo, PageRequest.of(page, size));

        List<EventSummaryResponse> content = result.getContent().stream()
                .map(event -> EventSummaryResponse.from(event, closingSoon(event)))
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public EventDetailResponse findAdminEvent(Long id) {
        Event event = eventRepository.findAdminEventById(id)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        return EventDetailResponse.from(event, closingSoon(event));
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> findDeletedEvents(int page, int size) {
        Page<Event> result = eventRepository.findDeletedEvents(PageRequest.of(page, size));

        List<EventSummaryResponse> content = result.getContent().stream()
                .map(event -> EventSummaryResponse.from(event, closingSoon(event)))
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    // 게시 중인 이벤트는 휴지통으로 보낼 수 없다(먼저 게시를 종료해야 함).
    // 생성 작업이 진행 중인 이벤트도 거부한다 — 안 그러면 휴지통으로 보낸 뒤에도
    // 백그라운드 생성이 끝나면서 삭제된 이벤트에 새 버전이 저장될 수 있다.
    @Transactional
    public void delete(Long id) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        if (event.getStatus() == EventStatus.PUBLISHED) {
            throw new EventException(EventErrorCode.PUBLISHED_EVENT_DELETE_FORBIDDEN);
        }
        if (generationJobStore.ofEvent(id).isPresent()) {
            throw new EventException(EventErrorCode.EVENT_GENERATING_DELETE_FORBIDDEN);
        }
        event.delete(OffsetDateTime.now(clock));
    }

    @Transactional
    public EventDetailResponse restore(Long id) {
        Event event = eventRepository.findByIdAndDeletedAtIsNotNull(id)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        event.restore();
        return EventDetailResponse.from(event, closingSoon(event));
    }

    /** 휴지통에서 영구 삭제. 되돌릴 수 없다 — event_versions 등 하위 데이터는 DB CASCADE 로 함께 지워진다. */
    @Transactional
    public void hardDelete(Long id) {
        Event event = eventRepository.findByIdAndDeletedAtIsNotNull(id)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        eventRepository.delete(event);
    }

    @Transactional
    public EventDetailResponse create(Long adminId, EventCreateRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new EventException(EventErrorCode.INVALID_PERIOD);
        }
        EventTemplate template = resolveTemplate(request.templateKey());
        Admin owner = adminRepository.getReferenceById(adminId);

        Event draft = Event.createDraft(
                owner,
                template,
                request.name().trim(),
                request.startAt(),
                request.endAt(),
                request.grade());
        Event saved = eventRepository.save(draft);
        return EventDetailResponse.from(saved, closingSoon(saved));
    }

    boolean closingSoon(Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED || event.deleted()) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (now.isBefore(event.getStartDate()) || !now.isBefore(event.getEndDate())) {
            return false;
        }
        return !now.isBefore(event.getEndDate().minus(CLOSING_SOON_WINDOW));
    }

    private EventTemplate resolveTemplate(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) {
            return null;
        }
        return eventTemplateRepository.findByKey(templateKey.trim())
                .filter(EventTemplate::isActive)
                .orElseThrow(() -> new EventException(EventErrorCode.TEMPLATE_NOT_FOUND));
    }
}
