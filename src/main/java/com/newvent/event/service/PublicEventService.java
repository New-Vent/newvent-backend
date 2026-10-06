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
     * 사용자에게 내려보낼 게시 HTML. <b>슬롯을 여기서 채운다.</b>
     *
     * ★ 저장된 HTML 은 슬롯이 빈 상태다 (Slots.clear 를 거쳐 저장된다).
     *   저장할 때 채우면 그 날짜가 굳어서, 이벤트에서 기간만 고쳐도 화면이 안 바뀐다.
     *   그래서 채우는 건 보여줄 때다 — 관리자 미리보기는 GenerationService.render 가,
     *   공개 조회는 여기가 한다.
     *
     * ★ latest 가 아니라 <b>게시 버전</b>을 본다
     *   GenerationService.render 는 versions.latest 를 보는 관리자 미리보기용이다.
     *   공개 페이지가 그걸 쓰면 게시하지 않은 초안이 사용자에게 보인다.
     *   여기서 보는 건 events.published_version_id 에 고정된 버전이다.
     *
     * ★ 참여 링크(CTA_LINK)는 아직 null 이다
     *   담을 칸이 없다(GenerateCommand.ctaUrl 주석 참고). 관리자 미리보기와 같은 상태다.
     *   Slots.fill 이 null 값을 건너뛰므로 버튼은 링크 없이 그대로 남는다.
     */
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
                .map(event -> PublicEventSummaryResponse.from(event, isClosingSoon(event, now)))
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }
}
