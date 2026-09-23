package com.newvent.event.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.newvent.event.dto.PageResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventTemplate;
import com.newvent.event.dto.EventCreateRequest;
import com.newvent.event.dto.EventDetailResponse;
import com.newvent.event.dto.EventSummaryResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventTemplateRepository;
import com.newvent.event.repository.InMemoryEventRepository;
import com.newvent.user.domain.MembershipGrade;

@Service
public class EventService {

    static final Duration CLOSING_SOON_WINDOW = Duration.ofDays(3);

    private final EventRepository eventRepository;
    private final EventTemplateRepository eventTemplateRepository;
    private final Clock clock;

    public EventService(
            EventRepository eventRepository,
            EventTemplateRepository eventTemplateRepository,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.eventTemplateRepository = eventTemplateRepository;
        this.clock = clock;
    }

    public PageResponse<EventSummaryResponse> findAdminEvents(
            String name,
            EventStatus status,
            OffsetDateTime periodFrom,
            OffsetDateTime periodTo,
            int page,
            int size) {
        List<Event> filtered = eventRepository.findAll().stream()
                .filter(event -> !event.deleted())
                .filter(event -> nameMatches(event, name))
                .filter(event -> status == null || event.getStatus() == status)
                .filter(event -> periodOverlaps(event, periodFrom, periodTo))
                .toList();

        long total = filtered.size();
        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<EventSummaryResponse> content = filtered.subList(fromIndex, toIndex).stream()
                .map(event -> EventSummaryResponse.from(event, closingSoon(event)))
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    public EventDetailResponse findAdminEvent(Long id) {
        Event event = eventRepository.findById(id)
                .filter(found -> !found.deleted())
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        return EventDetailResponse.from(event, closingSoon(event));
    }

    public EventDetailResponse create(EventCreateRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new EventException(EventErrorCode.INVALID_PERIOD);
        }
        EventTemplate template = resolveTemplate(request.templateKey());
        MembershipGrade grade = firstGradeOrNormal(request.targetGrades());

        Event draft = Event.createDraft(
                systemAdmin(),
                template,
                request.name().trim(),
                request.startAt(),
                request.endAt(),
                grade);
        draft.touchUpdatedAt(OffsetDateTime.now(clock));
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

    private com.newvent.admin.domain.Admin systemAdmin() {
        if (eventRepository instanceof InMemoryEventRepository memory) {
            return memory.systemAdmin();
        }
        return com.newvent.admin.domain.Admin.systemStub();
    }

    private static MembershipGrade firstGradeOrNormal(List<MembershipGrade> grades) {
        if (grades == null || grades.isEmpty() || grades.getFirst() == null) {
            return MembershipGrade.NORMAL;
        }
        return grades.getFirst();
    }

    private static boolean nameMatches(Event event, String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        return event.getTitle().toLowerCase(Locale.ROOT).contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean periodOverlaps(Event event, OffsetDateTime from, OffsetDateTime to) {
        if (from != null && event.getEndDate().isBefore(from)) {
            return false;
        }
        if (to != null && event.getStartDate().isAfter(to)) {
            return false;
        }
        return true;
    }
}
