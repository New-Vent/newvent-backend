package com.newvent.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.event.exception.EventNotFoundException;
import com.newvent.event.repository.EventRepository;

class PublicEventServiceTest {

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final PublicEventService publicEventService = new PublicEventService(eventRepository);

    @Test
    @DisplayName("공개 기간 안의 이벤트를 조회하면 그대로 반환한다")
    void 조회_성공() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(1), now.plusDays(1));
        when(eventRepository.findPublicEventById(1L, EventStatus.DRAFT)).thenReturn(Optional.of(event));

        Event found = publicEventService.getPublicEvent(1L);

        assertEquals(event, found);
    }

    @Test
    @DisplayName("존재하지 않는(또는 DRAFT·삭제된) 이벤트를 조회하면 EventNotFoundException을 던진다")
    void 조회_없는이벤트() {
        when(eventRepository.findPublicEventById(999L, EventStatus.DRAFT)).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> publicEventService.getPublicEvent(999L));
    }

    @Test
    @DisplayName("시작 전인 이벤트를 조회하면 EventNotAccessibleException을 던진다")
    void 조회_시작전() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.plusDays(1), now.plusDays(10));
        when(eventRepository.findPublicEventById(1L, EventStatus.DRAFT)).thenReturn(Optional.of(event));

        assertThrows(EventNotAccessibleException.class, () -> publicEventService.getPublicEvent(1L));
    }

    @Test
    @DisplayName("종료일이 지난 이벤트를 조회하면 EventNotAccessibleException을 던진다")
    void 조회_기간종료() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(10), now.minusDays(1));
        when(eventRepository.findPublicEventById(1L, EventStatus.DRAFT)).thenReturn(Optional.of(event));

        assertThrows(EventNotAccessibleException.class, () -> publicEventService.getPublicEvent(1L));
    }

    @Test
    @DisplayName("status가 ENDED면 기간과 무관하게 EventNotAccessibleException을 던진다")
    void 조회_상태ENDED() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.ENDED, now.minusDays(1), now.plusDays(1));
        when(eventRepository.findPublicEventById(1L, EventStatus.DRAFT)).thenReturn(Optional.of(event));

        assertThrows(EventNotAccessibleException.class, () -> publicEventService.getPublicEvent(1L));
    }

    @Test
    @DisplayName("종료일이 3일 이내로 남았으면 마감임박으로 판단한다")
    void 마감임박_true() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = newEvent(1L, EventStatus.PUBLISHED, now.minusDays(1), now.plusDays(2));

        assertTrue(publicEventService.isClosingSoon(event, now));
    }

    @Test
    @DisplayName("종료일이 정확히 3일 남았으면 마감임박으로 판단한다 (경계값)")
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

    // Event는 protected 기본 생성자뿐이라(생성 책임은 관리자 이벤트 쪽 PR #18), 리플렉션으로 픽스처를 만든다.
    private Event newEvent(Long id, EventStatus status, OffsetDateTime startDate, OffsetDateTime endDate) {
        Event event = BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "id", id);
        ReflectionTestUtils.setField(event, "status", status);
        ReflectionTestUtils.setField(event, "startDate", startDate);
        ReflectionTestUtils.setField(event, "endDate", endDate);
        return event;
    }
}
