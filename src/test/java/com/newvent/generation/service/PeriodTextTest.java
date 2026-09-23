package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 기간 표기. **스프링도 DB 도 안 띄운다.**
 */
class PeriodTextTest {

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }

    @Test
    @DisplayName("같은 해면 끝 날짜의 해를 생략한다 — 템플릿 5종과 같은 모양")
    void 같은_해() {
        assertEquals("2026.09.20 ~ 10.05",
                PeriodText.of(at("2026-09-20T00:00+09:00"), at("2026-10-05T23:59+09:00")));
    }

    @Test
    @DisplayName("해를 넘기면 끝 날짜에도 해를 찍는다")
    void 해를_넘김() {
        assertEquals("2026.12.20 ~ 2027.01.05",
                PeriodText.of(at("2026-12-20T00:00+09:00"), at("2027-01-05T23:59+09:00")));
    }

    @Test
    @DisplayName("★ UTC 로 들어와도 한국 날짜로 찍는다")
    void 시간대() {
        // 2026-09-19T20:00Z 는 한국시각 9월 20일 05:00 이다
        assertEquals("2026.09.20 ~ 10.05",
                PeriodText.of(at("2026-09-19T20:00Z"), at("2026-10-05T14:59Z")),
                "UTC 그대로 찍으면 9시 이전에 시작하는 이벤트가 하루 앞당겨 보입니다.");
    }

    @Test
    @DisplayName("한쪽만 있으면 그쪽만 찍는다 — end_date 는 nullable 이다")
    void 한쪽만() {
        assertEquals("2026.09.20 ~", PeriodText.of(at("2026-09-20T00:00+09:00"), null));
        assertEquals("~ 2026.10.05", PeriodText.of(null, at("2026-10-05T23:59+09:00")));
    }

    @Test
    @DisplayName("★ 둘 다 없으면 빈 문자열이 아니라 null 이다")
    void 둘_다_없음() {
        assertNull(PeriodText.of(null, null),
                "빈 문자열을 주면 Slots.fill() 이 기간 줄을 빈 채로 찍어서 레이아웃에 구멍이 납니다. "
                + "null 이면 그 슬롯을 아예 건드리지 않습니다.");
    }
}
