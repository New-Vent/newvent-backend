package com.newvent.generation.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * `events.start_date`·`end_date` 를 **화면에 찍을 한 줄**로 바꾼다.
 */
public final class PeriodText {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private PeriodText() {}


    public static String of(OffsetDateTime start, OffsetDateTime end) {
        LocalDate s = toSeoulDate(start);
        LocalDate e = toSeoulDate(end);

        if (s == null && e == null) return null;
        if (s == null) return "~ " + full(e);
        if (e == null) return full(s) + " ~";

        // 같은 해면 끝 날짜의 해를 생략한다 — 템플릿 표기와 같은 모양
        return full(s) + " ~ " + (s.getYear() == e.getYear() ? monthDay(e) : full(e));
    }

    private static LocalDate toSeoulDate(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(SEOUL).toLocalDate();
    }

    private static String full(LocalDate d) {
        return "%04d.%02d.%02d".formatted(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    private static String monthDay(LocalDate d) {
        return "%02d.%02d".formatted(d.getMonthValue(), d.getDayOfMonth());
    }
}
