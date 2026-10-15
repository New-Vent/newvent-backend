package com.newvent.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class EventClosingSoonTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-01T00:00:00+09:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-10-15T23:59:59+09:00");
    private static final OffsetDateTime WINDOW_START = END.minus(Event.CLOSING_SOON_WINDOW);

    @Test
    @DisplayName("종료 3일 전부터 종료 시각까지(포함) 마감 임박이다")
    void 마감_임박_구간() {
        Event event = event(EventStatus.PUBLISHED, START, END);

        assertThat(event.closingSoon(WINDOW_START.minusSeconds(1))).isFalse();
        assertThat(event.closingSoon(WINDOW_START)).isTrue();
        assertThat(event.closingSoon(END)).isTrue();
        assertThat(event.closingSoon(END.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("시작 전이면 종료가 가까워도 마감 임박이 아니다")
    void 시작_전() {
        OffsetDateTime lateStart = END.minusDays(1);
        Event event = event(EventStatus.PUBLISHED, lateStart, END);

        assertThat(event.closingSoon(lateStart.minusSeconds(1))).isFalse();
        assertThat(event.closingSoon(lateStart)).isTrue();
    }

    @Test
    @DisplayName("게시 중이 아니거나 삭제된 이벤트는 마감 임박이 아니다")
    void 게시_중이_아님() {
        Event deleted = event(EventStatus.PUBLISHED, START, END);
        deleted.delete(END.minusDays(5));

        assertThat(event(EventStatus.DRAFT, START, END).closingSoon(END)).isFalse();
        assertThat(event(EventStatus.ENDED, START, END).closingSoon(END)).isFalse();
        assertThat(deleted.closingSoon(END)).isFalse();
    }

    @Test
    @DisplayName("날짜가 비어 있어도 예외 없이 판정한다 — 종료일이 없으면 마감 임박이 아니다")
    void 날짜가_비어_있음() {
        assertThat(event(EventStatus.PUBLISHED, null, END).closingSoon(END)).isTrue();
        assertThat(event(EventStatus.PUBLISHED, START, null).closingSoon(END)).isFalse();
        assertThat(event(EventStatus.PUBLISHED, null, null).closingSoon(END)).isFalse();
    }

    private static Event event(EventStatus status, OffsetDateTime start, OffsetDateTime end) {
        Event event = BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "status", status);
        ReflectionTestUtils.setField(event, "startDate", start);
        ReflectionTestUtils.setField(event, "endDate", end);
        return event;
    }
}
