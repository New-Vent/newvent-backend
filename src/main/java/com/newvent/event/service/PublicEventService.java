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
import com.newvent.registry.PageShell;
import com.newvent.registry.Slots;

@Service
public class PublicEventService {

    private final EventRepository eventRepository;

    /**
     * 게시 페이지에 그릴 참여 방식. <b>구현은 participation 패키지에 있다</b> —
     * 이 패키지가 거기를 직접 보면 두 패키지가 서로를 보게 된다 ({@link PublishedGame} 주석).
     */
    private final PublishedGame publishedGame;

    public PublicEventService(EventRepository eventRepository, PublishedGame publishedGame) {
        this.eventRepository = eventRepository;
        this.publishedGame = publishedGame;
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

    // 공개 페이지 문서 — /e/{id} · 공개 상세 API 공통. 게시 버전이 없으면 null
    public String publishedPageOf(Event event) {
        String html = publishedHtmlOf(event);
        return html == null ? null : pageOf(event, html);
    }

    // 슬롯을 채운 조각을 완전한 문서로 감싼다 — 공개 페이지 · 관리자 미리보기 공통 (PageShell.standalone)
    // 종료일은 여기서 넣는다. 저장본에는 굳히지 않는다
    public String pageOf(Event event, String fragment) {
        // ★ 참여 방식까지 실어 보낸다 — runtime 이 body 에서 읽어 참여 API 본문을 만든다.
        //   설정이 없으면 null 이 가고, 그러면 고르는 칸도 안 그리고 data-game 도 안 적는다
        PublishedGame.Spec game = publishedGame.of(event.getId()).orElse(null);
        return PageShell.standalone(
                fragment, event.getTitle(), event.getEndDate(),
                event.getId(),
                game == null ? null : game.gameCode(),
                game == null ? null : game.config());
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
