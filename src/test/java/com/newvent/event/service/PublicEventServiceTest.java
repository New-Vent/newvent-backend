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

class PublicEventServiceTest {

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final PublicEventService publicEventService = new PublicEventService(eventRepository);

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
    @DisplayName("시작 전인 이벤트를 조회하면 EventNotAccessibleException으로 응답")
    void 조회_시작전() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.plusDays(1), now.plusDays(10));
        when(eventRepository.findPublicEventById(1L, EventStatus.DRAFT)).thenReturn(Optional.of(event));

        assertThrows(EventNotAccessibleException.class, () -> publicEventService.getPublicEvent(1L));
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
    @DisplayName("목록 조회 결과를 카테고리·마감임박 정보와 함께 PageResponse로 반환")
    void 목록조회_성공() {
        OffsetDateTime now = OffsetDateTime.now();
        EventTemplate template = EventTemplate.seed("signup", "가입 이벤트", null, "<html/>", true);
        Event event1 = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(1), now.plusDays(2), template);
        Event event2 = newEvent(2L, EventStatus.PUBLISHED, now.minusDays(1), now.plusDays(30), null);
        Page<Event> page = new PageImpl<>(List.of(event1, event2), PageRequest.of(0, 2), 5);
        when(eventRepository.findPublicEvents(any(), any(), any(), any(), any())).thenReturn(page);

        PageResponse<PublicEventSummaryResponse> result =
                publicEventService.getPublicEvents("signup", "가을", EventProgress.ONGOING, 0, 10);

        assertEquals(5, result.totalElements());
        assertEquals(2, result.content().size());
        assertEquals("signup", result.content().get(0).category());
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
