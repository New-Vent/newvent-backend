package com.newvent.event.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.admin.domain.Admin;
import com.newvent.admin.repository.AdminRepository;
import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventProgress;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventTemplate;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.dto.request.EventCreateRequest;
import com.newvent.event.dto.request.EventUpdateRequest;
import com.newvent.event.dto.response.EventCountsResponse;
import com.newvent.event.dto.response.EventDetailResponse;
import com.newvent.event.dto.response.EventSummaryResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventTemplateRepository;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.generation.service.GenerationJobStore;

@Service
public class EventService {

    public static final Duration CLOSING_SOON_WINDOW = Duration.ofDays(3);

    // Long 범위를 넘지 않게 18자리까지만 ID 로 본다.
    private static final Pattern ID_KEYWORD = Pattern.compile("\\d{1,18}");

    private final EventRepository eventRepository;
    private final EventTemplateRepository eventTemplateRepository;
    private final EventVersionRepository eventVersionRepository;
    private final AdminRepository adminRepository;
    private final GenerationJobStore generationJobStore;
    private final Clock clock;

    public EventService(
            EventRepository eventRepository,
            EventTemplateRepository eventTemplateRepository,
            EventVersionRepository eventVersionRepository,
            AdminRepository adminRepository,
            GenerationJobStore generationJobStore,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.eventTemplateRepository = eventTemplateRepository;
        this.eventVersionRepository = eventVersionRepository;
        this.adminRepository = adminRepository;
        this.generationJobStore = generationJobStore;
        this.clock = clock;
    }

    /** 검색어(name)가 숫자뿐이면 이름이 맞는 것과 ID 가 같은 것을 함께 찾는다. */
    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> findAdminEvents(
            String name,
            EventStatus status,
            EventProgress progress,
            OffsetDateTime periodFrom,
            OffsetDateTime periodTo,
            int page,
            int size) {
        if (periodFrom != null && periodTo != null && periodFrom.isAfter(periodTo)) {
            throw new EventException(EventErrorCode.INVALID_SEARCH_PERIOD);
        }

        String keyword = name == null || name.isBlank() ? null : name.trim();
        String namePattern = keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        Long idMatch = keyword != null && ID_KEYWORD.matcher(keyword).matches() ? Long.valueOf(keyword) : null;

        Page<Event> result = eventRepository.findAdminEvents(
                namePattern, idMatch, status, progress, OffsetDateTime.now(clock),
                periodFrom, periodTo, PageRequest.of(page, size));
        return toSummaryPage(result, page, size);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse findAdminEvent(Long id) {
        Event event = findActiveEvent(id);
        return EventDetailResponse.from(event, closingSoon(event));
    }

    @Transactional(readOnly = true)
    public EventCountsResponse findEventCounts() {
        long published = 0;
        long draft = 0;
        long ended = 0;
        long total = 0;
        for (Object[] row : eventRepository.countGroupedByStatus()) {
            EventStatus status = (EventStatus) row[0];
            long count = (Long) row[1];
            total += count;
            switch (status) {
                case PUBLISHED -> published = count;
                case DRAFT -> draft = count;
                case ENDED -> ended = count;
            }
        }
        return new EventCountsResponse(total, published, draft, ended);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> findDeletedEvents(int page, int size) {
        Page<Event> result = eventRepository.findDeletedEvents(PageRequest.of(page, size));
        return toSummaryPage(result, page, size);
    }

    private PageResponse<EventSummaryResponse> toSummaryPage(Page<Event> result, int page, int size) {
        Map<Long, Integer> latestVersionNos = latestVersionNos(result.getContent());
        List<EventSummaryResponse> content = result.getContent().stream()
                .map(event -> EventSummaryResponse.from(
                        event, closingSoon(event), latestVersionNos.get(event.getId())))
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    private Map<Long, Integer> latestVersionNos(List<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = events.stream().map(Event::getId).toList();
        return eventVersionRepository.findLatestVersionNos(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Integer) row[1]));
    }

    // 게시 중인 이벤트는 휴지통으로 보낼 수 없다(먼저 게시를 내리거나 종료해야 함).
    // 생성 작업이 진행 중인 이벤트도 거부한다 — 안 그러면 휴지통으로 보낸 뒤에도 백그라운드 생성이 끝나면서 삭제된 이벤트에 새 버전이 저장될 수 있다.
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

    // 휴지통에서 영구 삭제. 되돌릴 수 없다 — event_versions 등 하위 데이터는 DB CASCADE 로 함께 지워진다
    @Transactional
    public void hardDelete(Long id) {
        Event event = eventRepository.findByIdAndDeletedAtIsNotNull(id)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        eventRepository.delete(event);
    }

    // 게시(DRAFT→PUBLISHED) 및 재게시(PUBLISHED 상태에서 다른 버전으로 교체)
    // 선택한 버전이 체크포인트가 아니면 게시 시점에 자동으로 체크포인트 처리한다
    @Transactional
    public EventDetailResponse publish(Long id, Long versionId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        if (event.getStatus() == EventStatus.ENDED) {
            throw new EventException(EventErrorCode.EVENT_ENDED_PUBLISH_FORBIDDEN);
        }
        // 종료일시가 지난 채로 게시하면 사용자에게 한 번도 보이지 않은 채 곧 자동 종료된다. 시작일은 보지 않는다
        if (event.periodEnded(OffsetDateTime.now(clock))) {
            throw new EventException(EventErrorCode.EVENT_PERIOD_ENDED_PUBLISH_FORBIDDEN);
        }
        EventVersion version = eventVersionRepository.findByIdAndEventId(versionId, id)
                .orElseThrow(() -> new EventException(EventErrorCode.VERSION_NOT_FOUND));

        version.markCheckpoint(OffsetDateTime.now(clock));
        event.publish(version);
        return EventDetailResponse.from(event, closingSoon(event));
    }

    @Transactional
    public EventDetailResponse create(Long adminId, EventCreateRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new EventException(EventErrorCode.INVALID_PERIOD);
        }
        EventTemplate template = resolveTemplate(request.templateKey(), adminId);
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

    /** 보낸 필드만 바꾼다. null 은 기존 값 유지, templateKey 가 빈 문자열이면 템플릿을 해제한다. */
    @Transactional
    public EventDetailResponse update(Long adminId, Long id, EventUpdateRequest request) {
        Event event = findActiveEvent(id);
        if (event.getOwnerAdmin() == null || !adminId.equals(event.getOwnerAdmin().getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "이벤트 소유 관리자만 수정할 수 있습니다.");
        }
        if (event.editLocked(OffsetDateTime.now(clock))) {
            throw new EventException(EventErrorCode.EVENT_ENDED_NOT_EDITABLE);
        }
        if (event.published() && templateChanged(event, request.templateKey())) {
            throw new EventException(EventErrorCode.PUBLISHED_EVENT_TEMPLATE_NOT_EDITABLE);
        }

        OffsetDateTime startAt = request.startAt() != null ? request.startAt() : event.getStartDate();
        OffsetDateTime endAt = request.endAt() != null ? request.endAt() : event.getEndDate();
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new EventException(EventErrorCode.INVALID_PERIOD);
        }
        EventTemplate template = request.templateKey() != null
                ? resolveTemplate(request.templateKey(), adminId)
                : event.getTemplate();

        event.updateInfo(
                request.name() != null ? request.name().trim() : event.getTitle(),
                template,
                startAt,
                endAt,
                request.grade() != null ? request.grade() : event.getGrade());
        // updatedAt 은 flush 시점에 Auditing 이 채우므로, 응답에 새 수정일을 담으려면 먼저 flush 한다.
        eventRepository.flush();
        return EventDetailResponse.from(event, closingSoon(event));
    }

    // 상태 변경 API 는 종료(PUBLISHED → ENDED)만 한다. 게시는 게시 API 로 한다
    @Transactional
    public EventDetailResponse changeStatus(Long id, EventStatus target) {
        Event event = findActiveEvent(id);
        if (target != EventStatus.ENDED) {
            throw new EventException(EventErrorCode.UNSUPPORTED_STATUS_CHANGE);
        }
        if (!event.published()) {
            throw new EventException(EventErrorCode.EVENT_NOT_ENDABLE);
        }
        event.end();
        eventRepository.flush();
        return EventDetailResponse.from(event, closingSoon(event));
    }

    // 게시 내리기(PUBLISHED → DRAFT) - 종료 전까지만 가능
    // 종료 시각이 지났는데 자동 종료가 아직 안 돈 이벤트도 막기 - 내리면 DRAFT 가 되어 수정 잠금이 풀리고, 종료된 이벤트를 수정·재게시할 수 있게 되기 때문
    @Transactional
    public EventDetailResponse unpublish(Long id) {
        Event event = findActiveEvent(id);
        if (!event.published()) {
            throw new EventException(EventErrorCode.EVENT_NOT_UNPUBLISHABLE);
        }
        if (event.editLocked(OffsetDateTime.now(clock))) {
            throw new EventException(EventErrorCode.EVENT_ENDED_NOT_EDITABLE);
        }
        event.unpublish();
        eventRepository.flush();
        return EventDetailResponse.from(event, closingSoon(event));
    }

    private Event findActiveEvent(Long id) {
        return eventRepository.findAdminEventById(id)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
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

    private boolean templateChanged(Event event, String templateKey) {
        if (templateKey == null) {
            return false;
        }
        String requested = templateKey.isBlank() ? null : templateKey.trim();
        return !Objects.equals(requested, event.templateCode());
    }

    private EventTemplate resolveTemplate(String templateKey, Long adminId) {
        if (templateKey == null || templateKey.isBlank()) {
            return null;
        }
        return eventTemplateRepository.findByKey(templateKey.trim())
                .filter(EventTemplate::isActive)
                .filter(t -> t.visibleTo(adminId))
                .orElseThrow(() -> new EventException(EventErrorCode.TEMPLATE_NOT_FOUND));
    }
}
