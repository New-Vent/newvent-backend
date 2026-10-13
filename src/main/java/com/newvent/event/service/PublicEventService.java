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

    // REQ-EVT-15: 종료 며칠 전부터 "마감 임박"으로 표시할지는 기획 확정 전이라 3일로 임시 지정
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

    /**
     * 사용자에게 내려보낼 게시 HTML.
     *
     * ★ 참여 링크를 채운다. 전에는 <b>항상 null</b> 이어서 cta-link 슬롯이 끝까지 비어 있었다 —
     *   관리자가 디자인한 「참여하기」 버튼이 아무 데도 가지 않는 장식이었다.
     *   주소는 {@link ParticipationLink} 한 곳에서만 만든다.
     *
     * ★ 참여 버튼 블록은 그대로 둔다. <b>버튼은 항상 있는 것으로 본다</b> —
     *   Block.CTA 가 required=true 라 LLM 이 빠뜨릴 수 없고, 서버도 떼지 않는다.
     *   그 버튼이 실제로 참여까지 가는지는 호스트(프론트)가 클릭을 받아 정한다.
     */
    public String publishedHtmlOf(Event event) {
        EventVersion published = event.getPublishedVersion();
        if (published == null) {
            return null;
        }
        return Slots.fill(
                published.getHtmlContent(),
                PeriodText.of(event.getStartDate(), event.getEndDate()),
                ParticipationLink.of(event.getId()));
    }

     // 목록 썸네일용 HTML, 슬롯은 publishedHtmlOf 와 같은 규칙으로 채운 뒤 자른다(기간이 hero 안에 있다)
    public String thumbnailHtmlOf(Event event) {
        EventVersion published = event.getPublishedVersion();
        return published == null ? null : ThumbnailHtml.of(event, published.getHtmlContent());
    }

    // 마감임박은 이미 시작한(진행중) 이벤트에만 표시한다 — 시작 전 이벤트가 종료일만 가까워서
    // closingSoon=true로 잘못 뜨는 것을 막는다 (리뷰 반영: tnqlsqkr).
    public boolean isClosingSoon(Event event, OffsetDateTime now) {
        if (event.getStartDate() != null && now.isBefore(event.getStartDate())) {
            return false;
        }
        if (event.getEndDate() == null) {
            return false;
        }
        return !now.isAfter(event.getEndDate())
                && !now.plusDays(CLOSING_SOON_DAYS).isBefore(event.getEndDate());
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
