package com.newvent.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventProgress;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventTemplate;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.dto.response.PublicEventSummaryResponse;
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.event.exception.EventNotFoundException;
import com.newvent.event.repository.EventRepository;
import com.newvent.user.domain.MembershipGrade;

class PublicEventServiceTest {

    private final EventRepository eventRepository = mock(EventRepository.class);

    /** 기본은 참여를 받지 않는 이벤트다. 받는 쪽은 아래 게임 테스트가 따로 지정한다 */
    private final PublishedGame publishedGame = mock(PublishedGame.class);

    private final PublicEventService publicEventService =
            new PublicEventService(eventRepository, publishedGame);

    PublicEventServiceTest() {
        when(publishedGame.of(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("공개 기간 안의 이벤트를 조회하면 그대로 반환")
    void 조회_성공() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(1), now.plusDays(1));
        when(eventRepository.findPublicEventById(1L, EventStatus.DRAFT)).thenReturn(Optional.of(event));

        Event found = publicEventService.getPublicEvent(1L);

        assertEquals(event, found);
    }

    @Test
    @DisplayName("존재하지 않는(또는 DRAFT·삭제된) 이벤트를 조회하면 EventNotFoundException으로 응답")
    void 조회_없는이벤트() {
        when(eventRepository.findPublicEventById(999L, EventStatus.DRAFT)).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> publicEventService.getPublicEvent(999L));
    }

    @Test
    @DisplayName("게시된 이벤트는 시작 전에도 조회할 수 있다")
    void 조회_시작전() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.plusDays(1), now.plusDays(10));
        when(eventRepository.findPublicEventById(1L, EventStatus.DRAFT)).thenReturn(Optional.of(event));

        assertEquals(event, publicEventService.getPublicEvent(1L));
    }

    @Test
    @DisplayName("종료일이 지난 이벤트를 조회하면 EventNotAccessibleException으로 응답")
    void 조회_기간종료() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(10), now.minusDays(1));
        when(eventRepository.findPublicEventById(1L, EventStatus.DRAFT)).thenReturn(Optional.of(event));

        assertThrows(EventNotAccessibleException.class, () -> publicEventService.getPublicEvent(1L));
    }

    @Test
    @DisplayName("status가 ENDED면 기간과 무관하게 EventNotAccessibleException으로 응답")
    void 조회_상태ENDED() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.ENDED, now.minusDays(1), now.plusDays(1));
        when(eventRepository.findPublicEventById(1L, EventStatus.DRAFT)).thenReturn(Optional.of(event));

        assertThrows(EventNotAccessibleException.class, () -> publicEventService.getPublicEvent(1L));
    }

    @Test
    @DisplayName("종료일이 3일 이내로 남았으면 마감임박으로 판단")
    void 마감임박_true() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(1), now.plusDays(2));

        assertTrue(publicEventService.isClosingSoon(event, now));
    }

    @Test
    @DisplayName("종료일이 정확히 3일 남았으면 마감임박으로 판단(경계값)")
    void 마감임박_경계값_3일() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(1), now.plusDays(3));

        assertTrue(publicEventService.isClosingSoon(event, now));
    }

    @Test
    @DisplayName("종료일이 3일보다 많이 남았으면 마감임박이 아니다")
    void 마감임박_false() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(1), now.plusDays(10));

        assertFalse(publicEventService.isClosingSoon(event, now));
    }

    @Test
    @DisplayName("이미 종료일이 지났으면 마감임박이 아니다")
    void 마감임박_이미종료() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(10), now.minusDays(1));

        assertFalse(publicEventService.isClosingSoon(event, now));
    }

    @Test
    @DisplayName("아직 시작 전이면 종료일이 3일 이내라도 마감임박이 아니다 (리뷰 반영: tnqlsqkr)")
    void 마감임박_시작전이벤트는_false() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.plusDays(1), now.plusDays(2));

        assertFalse(publicEventService.isClosingSoon(event, now));
    }

    @Test
    @DisplayName("마감임박 기간은 관리자 쪽과 같은 Event.CLOSING_SOON_WINDOW 를 따른다")
    void 마감임박_기간은_Event_기준() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-13T10:00:00+09:00");
        OffsetDateTime windowEdge = now.plus(Event.CLOSING_SOON_WINDOW);
        Event atEdge = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(1), windowEdge);
        Event justOutside = newEvent(2L, EventStatus.PUBLISHED, now.minusDays(1), windowEdge.plusSeconds(1));

        assertTrue(publicEventService.isClosingSoon(atEdge, now));
        assertFalse(publicEventService.isClosingSoon(justOutside, now));
        assertEquals(atEdge.closingSoon(now), publicEventService.isClosingSoon(atEdge, now));
    }

    @Test
    @DisplayName("목록 조회 결과를 카테고리·마감임박 정보와 함께 PageResponse로 반환")
    void 목록조회_성공() {
        OffsetDateTime now = OffsetDateTime.now();
        EventTemplate template = EventTemplate.seed("signup", "가입 이벤트", null, "<html/>", true);
        Event event1 = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(1), now.plusDays(2), template);
        ReflectionTestUtils.setField(event1, "grade", MembershipGrade.EXCELLENT);
        Event event2 = newEvent(2L, EventStatus.PUBLISHED, now.minusDays(1), now.plusDays(30), null);
        Page<Event> page = new PageImpl<>(List.of(event1, event2), PageRequest.of(0, 2), 5);
        when(eventRepository.findPublicEvents(any(), any(), any(), any(), any())).thenReturn(page);

        PageResponse<PublicEventSummaryResponse> result =
                publicEventService.getPublicEvents("signup", "가을", EventProgress.ONGOING, 0, 10);

        assertEquals(5, result.totalElements());
        assertEquals(2, result.content().size());
        assertEquals("signup", result.content().get(0).category());
        assertEquals(MembershipGrade.EXCELLENT, result.content().get(0).grade());
        assertTrue(result.content().get(0).closingSoon());
        assertFalse(result.content().get(1).closingSoon());
    }

    @Test
    @DisplayName("progress는 EventProgress.name() 문자열로, keyword는 소문자 LIKE 패턴으로 변환해서 전달")
    void 목록조회_progress_키워드_변환() {
        when(eventRepository.findPublicEvents(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        publicEventService.getPublicEvents("signup", "가을Event", EventProgress.ENDED, 1, 20);

        verify(eventRepository).findPublicEvents(
                eq("signup"), eq("%가을event%"), eq("ENDED"), any(OffsetDateTime.class), eq(PageRequest.of(1, 20)));
    }

    @Test
    @DisplayName("keyword 안의 % _ 는 글자 그대로 찾도록 이스케이프해서 전달, 공백뿐이면 조건 없음")
    void 목록조회_키워드_와일드카드_이스케이프() {
        when(eventRepository.findPublicEvents(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        publicEventService.getPublicEvents(null, "100%", null, 0, 10);
        publicEventService.getPublicEvents(null, "a_b", null, 0, 10);
        publicEventService.getPublicEvents(null, "   ", null, 0, 10);

        verify(eventRepository).findPublicEvents(
                isNull(), eq("%100!%%"), isNull(), any(OffsetDateTime.class), eq(PageRequest.of(0, 10)));
        verify(eventRepository).findPublicEvents(
                isNull(), eq("%a!_b%"), isNull(), any(OffsetDateTime.class), eq(PageRequest.of(0, 10)));
        verify(eventRepository).findPublicEvents(
                isNull(), isNull(), isNull(), any(OffsetDateTime.class), eq(PageRequest.of(0, 10)));
    }

    @Test
    @DisplayName("progress를 지정하지 않으면 리포지토리에 null을 전달")
    void 목록조회_progress_없으면_null전달() {
        when(eventRepository.findPublicEvents(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        publicEventService.getPublicEvents(null, null, null, 0, 10);

        verify(eventRepository).findPublicEvents(
                isNull(), isNull(), isNull(), any(OffsetDateTime.class), eq(PageRequest.of(0, 10)));
    }

    // ── 게시 HTML 의 슬롯 채우기 ─────────────────────────────────

    @Test
    @DisplayName("게시 HTML 의 기간 슬롯이 채워진다 — 비어 있으면 사용자 화면에 날짜가 안 보인다")
    void 게시HTML_기간슬롯_채움() {
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-31T23:59:59+09:00"));
        setPublishedHtml(event, "<section data-block=\"hero\">"
                + "<p data-slot=\"period\">기간</p></section>");

        String html = publicEventService.publishedHtmlOf(event);

        assertTrue(html.contains("2026.10.01 ~ 10.31"),
                "기간이 채워져야 한다. 실제: " + html);
    }

    @Test
    @DisplayName("게시 버전이 없으면 null — 저장된 초안을 대신 내보내지 않는다")
    void 게시HTML_게시버전없음() {
        Event event = newEvent(1L, EventStatus.PUBLISHED, null, null);

        assertNull(publicEventService.publishedHtmlOf(event));
    }

    @Test
    @DisplayName("기간이 비어 있으면 슬롯을 건드리지 않는다 — 원본 문구가 남는다")
    void 게시HTML_기간없음() {
        Event event = newEvent(1L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(event, "<p data-slot=\"period\">기간 미정</p>");

        String html = publicEventService.publishedHtmlOf(event);

        assertTrue(html.contains("기간 미정"), "실제: " + html);
    }

    // ── 참여 버튼 ────────────────────────────────────────────────

    /** 참여 버튼이 있는 게시 조각. 템플릿 5종과 같은 모양이다 — {@code <button>} 이라 data-href 로 받는다. */
    private static final String WITH_CTA =
            "<section data-block=\"hero\"><h1>제목</h1></section>"
            + "<section data-block=\"cta\">"
            + "<button data-slot=\"cta-link\">참여하기</button></section>";

    @Test
    @DisplayName("★ 참여 버튼에 참여 화면 주소가 채워진다 — 전에는 항상 null 이라 슬롯이 끝까지 비어 있었다")
    void 게시HTML_참여주소를_채운다() {
        Event event = newEvent(7L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(event, WITH_CTA);

        String html = publicEventService.publishedHtmlOf(event);

        assertTrue(html.contains("data-href=\"/events/7\""),
                "참여 화면 주소가 들어가야 한다. 실제: " + html);
        assertTrue(html.contains("참여하기"),
                "버튼 문구는 그대로 남는다 — Slot.Kind.LINK 는 속성만 건드린다. 실제: " + html);
    }

    @Test
    @DisplayName("<a> 면 href 로 채운다 — 백지 생성 결과가 이 모양이다")
    void 게시HTML_a태그는_href로채운다() {
        Event event = newEvent(7L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(event,
                "<section data-block=\"cta\">"
                + "<a href=\"#\" class=\"btn\" data-slot=\"cta-link\">지금 참여하기</a></section>");

        String html = publicEventService.publishedHtmlOf(event);

        assertTrue(html.contains("href=\"/events/7\""), "실제: " + html);
        assertTrue(html.contains("지금 참여하기"), "실제: " + html);
    }

    @Test
    @DisplayName("참여 버튼 블록은 서버가 떼지 않는다 — 버튼은 항상 있는 것으로 본다")
    void 게시HTML_버튼을_떼지않는다() {
        Event event = newEvent(7L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(event, WITH_CTA);

        String html = publicEventService.publishedHtmlOf(event);

        assertTrue(html.contains("data-block=\"cta\""), "실제: " + html);
    }

    // ── 공개 페이지 문서 (PageShell.standalone) ──────────────────────

    @Test
    @DisplayName("공개 페이지 문서 — 슬롯을 채우고, 타이머에 종료 시각을 묶고, 게시 버전이 없으면 null")
    void 공개문서() {
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-31T23:59:59+09:00"));
        setPublishedHtml(event, "<section data-block=\"hero\"><p data-slot=\"period\">기간</p>"
                + "<span data-behavior=\"countdown\"></span></section>");

        String page = publicEventService.publishedPageOf(event);

        assertTrue(page.startsWith("<!DOCTYPE html>"), page);
        assertTrue(page.contains("2026.10.01 ~ 10.31"), "기간이 채워져야 한다. 실제: " + page);
        assertTrue(page.contains("data-until=\"2026-10-31T14:59:59Z\""), page);
        assertTrue(page.contains("/assets/event-runtime.js"), page);
        assertNull(publicEventService.publishedPageOf(newEvent(2L, EventStatus.PUBLISHED, null, null)));
    }

    // ── 옛 페이지 호환 — 조각 응답에 참여 이름표가 심긴다 ─────────

    @Test
    @DisplayName("★ 조각 응답 — 참여 이름표가 없는 옛 페이지에 이름표를 심어 돌려준다")
    void 게시HTML_옛페이지에_이름표를_심는다() {
        Event event = newEvent(7L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(event,
                "<section data-block=\"hero\"><h1>제목</h1></section>"
                + "<section data-block=\"cta\"><button class=\"cta-btn\">참여하기</button></section>");

        String html = publicEventService.publishedHtmlOf(event);

        assertTrue(html.contains("data-behavior=\"participate\""),
                "옛 페이지에도 참여 이름표가 심겨야 한다. 실제: " + html);
    }

    @Test
    @DisplayName("이미 이름표가 있으면 그대로 — 새 페이지는 비용이 없다")
    void 게시HTML_이름표가있으면_그대로다() {
        Event event = newEvent(7L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(event,
                "<section data-block=\"cta\">"
                + "<button data-behavior=\"participate\">참여하기</button></section>");

        String html = publicEventService.publishedHtmlOf(event);

        // 하나만 있어야 한다. 두 번 심기면 눌렀을 때 어느 버튼이 참여인지 모른다
        assertEquals(1, html.split("data-behavior=\"participate\"", -1).length - 1,
                "참여 이름표가 두 번 심기면 안 된다. 실제: " + html);
    }

    // ── 게시 페이지의 참여 ──────────────────────────────────────

    /** 참여 버튼이 심긴 조각. 생성 경로가 cta 의 첫 버튼에 participate 를 단다 */
    private static final String WITH_PARTICIPATE =
            "<section data-block=\"hero\"><h1>제목</h1></section>"
            + "<section data-block=\"cta\">"
            + "<button data-behavior=\"participate\">참여하기</button></section>";

    @Test
    @DisplayName("★ 참여를 받으면 body 에 이벤트 id 와 방식을 적는다 — runtime 이 여기서 읽는다")
    void 공개문서_참여정보를_싣는다() {
        Event event = newEvent(7L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(event, WITH_PARTICIPATE);
        when(publishedGame.of(7L)).thenReturn(Optional.of(
                new PublishedGame.Spec("LUCKY_POUCH", Map.of("pouches", List.of(
                        Map.of("pouchIndex", 1, "winProbability", 30, "prizeName", "한우세트"),
                        Map.of("pouchIndex", 2, "winProbability", 10, "prizeName", "쿠폰"))))));

        String page = publicEventService.publishedPageOf(event);

        assertTrue(page.contains("data-event-id=\"7\""), page);
        assertTrue(page.contains("data-game=\"LUCKY_POUCH\""), page);
        assertTrue(page.contains("data-pouch=\"1\""), "고르는 칸이 그려져야 한다: " + page);
        assertTrue(page.contains("data-pouch=\"2\""), page);
    }

    @Test
    @DisplayName("참여를 받지 않으면 data-game 을 적지 않는다 — runtime 이 '준비 중' 으로 안내한다")
    void 공개문서_설정이없으면_방식을안적는다() {
        Event event = newEvent(7L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(event, WITH_PARTICIPATE);
        when(publishedGame.of(7L)).thenReturn(Optional.empty());

        String page = publicEventService.publishedPageOf(event);

        assertFalse(page.contains("data-game="), page);
        assertFalse(page.contains("ev-game"), "고르는 칸도 그리지 않는다: " + page);
    }

    @Test
    @DisplayName("참여 버튼이 없으면 고르는 칸도 그리지 않는다 — 내지 못하는 입력을 띄우지 않는다")
    void 공개문서_참여버튼이없으면_칸도없다() {
        Event event = newEvent(7L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(event, "<section data-block=\"hero\">"
                + "<span data-behavior=\"countdown\"></span></section>");
        when(publishedGame.of(7L)).thenReturn(Optional.of(
                new PublishedGame.Spec("PRE_REGISTRATION", Map.of())));

        String page = publicEventService.publishedPageOf(event);

        assertFalse(page.contains("ev-game"), page);
        assertTrue(page.contains("data-game=\"PRE_REGISTRATION\""), "방식은 적는다 — 버튼만 없는 것이다");
    }

    @Test
    @DisplayName("기본형은 고르는 칸을 남기지 않는다 — 빈 상자는 여백만 만든다")
    void 공개문서_기본형은_빈칸을안남긴다() {
        Event event = newEvent(7L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(event, WITH_PARTICIPATE);
        when(publishedGame.of(7L)).thenReturn(Optional.of(
                new PublishedGame.Spec("BASIC", Map.of("resultMode", "GUARANTEED", "prizeName", "쿠폰"))));

        String page = publicEventService.publishedPageOf(event);

        assertFalse(page.contains("ev-game"), page);
        assertTrue(page.contains("data-game=\"BASIC\""), page);
    }

    // ── 목록 썸네일 (hero 만 잘라내기) ───────────────────────────

    @Test
    @DisplayName("썸네일은 hero 블록만 담고, 루트 래퍼(테마 클래스)는 껍데기로 유지하며 기간 슬롯이 채워진다")
    void 썸네일_hero만_루트유지() {
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-31T23:59:59+09:00"));
        setPublishedHtml(event, "<div class=\"ev-container event-page theme-sale\">"
                + "<section data-block=\"hero\"><h1>제목</h1><p data-slot=\"period\">기간</p></section>"
                + "<section data-block=\"benefits\"><h2>혜택</h2></section>"
                + "</div>");

        String thumb = publicEventService.thumbnailHtmlOf(event);

        assertTrue(thumb.contains("theme-sale"), "실제: " + thumb);
        assertTrue(thumb.contains("제목"), "실제: " + thumb);
        assertTrue(thumb.contains("2026.10.01 ~ 10.31"), "실제: " + thumb);
        assertFalse(thumb.contains("benefits"), "실제: " + thumb);
    }

    @Test
    @DisplayName("루트 래퍼가 없는 조각이면 hero 만 반환")
    void 썸네일_루트없음() {
        Event event = newEvent(1L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(event, "<section data-block=\"hero\"><h1>제목</h1></section>"
                + "<section data-block=\"cta\"><button>참여</button></section>");

        String thumb = publicEventService.thumbnailHtmlOf(event);

        assertTrue(thumb.contains("제목"));
        assertFalse(thumb.contains("참여"));
    }

    @Test
    @DisplayName("게시 버전이 없거나 hero 가 없으면 썸네일은 null")
    void 썸네일_없음() {
        Event noVersion = newEvent(1L, EventStatus.PUBLISHED, null, null);
        assertNull(publicEventService.thumbnailHtmlOf(noVersion));

        Event noHero = newEvent(2L, EventStatus.PUBLISHED, null, null);
        setPublishedHtml(noHero, "<section data-block=\"cta\"><button>참여</button></section>");
        assertNull(publicEventService.thumbnailHtmlOf(noHero));
    }

    private void setPublishedHtml(Event event, String html) {
        EventVersion version = BeanUtils.instantiateClass(EventVersion.class);
        ReflectionTestUtils.setField(version, "htmlContent", html);
        ReflectionTestUtils.setField(event, "publishedVersion", version);
    }

    private Event newEvent(Long id, EventStatus status, OffsetDateTime startDate, OffsetDateTime endDate) {
        return newEvent(id, status, startDate, endDate, null);
    }

    private Event newEvent(
            Long id, EventStatus status, OffsetDateTime startDate, OffsetDateTime endDate, EventTemplate template) {
        Event event = BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "id", id);
        ReflectionTestUtils.setField(event, "status", status);
        ReflectionTestUtils.setField(event, "startDate", startDate);
        ReflectionTestUtils.setField(event, "endDate", endDate);
        ReflectionTestUtils.setField(event, "template", template);
        return event;
    }
}
