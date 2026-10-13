package com.newvent.event.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventProgress;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.dto.response.PublicEventSummaryResponse;
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.event.exception.EventNotFoundException;
import com.newvent.event.repository.EventRepository;
import com.newvent.generation.service.PeriodText;
import com.newvent.registry.Slots;

@Service
public class PublicEventService {

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

    //사용자에게 내려보낼 게시 HTML
    public String publishedHtmlOf(Event event) {
        EventVersion published = event.getPublishedVersion();
        if (published == null) {
            return null;
        }
        return Slots.fill(
                published.getHtmlContent(),
                PeriodText.of(event.getStartDate(), event.getEndDate()),
                null);
    }

     // 목록 썸네일용 HTML, 슬롯은 publishedHtmlOf 와 같은 규칙으로 채운 뒤 자른다(기간이 hero 안에 있다)
    public String thumbnailHtmlOf(Event event) {
        EventVersion published = event.getPublishedVersion();
        return published == null ? null : ThumbnailHtml.of(event, published.getHtmlContent());
    }

    // 관리자 화면과 같은 기준을 써야 해서 계산은 Event.closingSoon 에만 둔다
    public boolean isClosingSoon(Event event, OffsetDateTime now) {
        return event.closingSoon(now);
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicEventSummaryResponse> getPublicEvents(
            String category, String keyword, EventProgress progress, int page, int size) {
        OffsetDateTime now = OffsetDateTime.now();
        String progressName = progress == null ? null : progress.name();
        String keywordPattern = keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";

        Page<Event> result = eventRepository.findPublicEvents(
                category, keywordPattern, progressName, now, PageRequest.of(page, size));

        List<PublicEventSummaryResponse> content = result.getContent().stream()
                .map(event -> PublicEventSummaryResponse.from(event, isClosingSoon(event, now), thumbnailHtmlOf(event)))
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }
}
