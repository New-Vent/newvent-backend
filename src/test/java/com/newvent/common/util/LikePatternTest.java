package com.newvent.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LikePatternTest {

    @Test
    @DisplayName("검색어는 앞뒤 공백을 떼고 소문자 %값% 패턴이 된다")
    void 소문자_부분일치_패턴() {
        assertEquals("%가을event%", LikePattern.contains("  가을Event "));
    }

    @Test
    @DisplayName("null · 빈 값 · 공백뿐인 검색어는 조건 없음(null)이다")
    void 비면_조건_없음() {
        assertNull(LikePattern.contains(null));
        assertNull(LikePattern.contains(""));
        assertNull(LikePattern.contains("   "));
    }

    @Test
    @DisplayName("값 안의 % _ 와 이스케이프 문자 자체는 글자 그대로 찾도록 이스케이프한다")
    void 와일드카드_이스케이프() {
        assertEquals("%100!%%", LikePattern.contains("100%"));
        assertEquals("%a!_b%", LikePattern.contains("a_b"));
        assertEquals("%a!!b%", LikePattern.contains("a!b"));
    }

    @Test
    @DisplayName("이스케이프 문자를 먼저 두 배로 만든다 — 뒤에 붙인 이스케이프가 다시 두 배가 되지 않는다")
    void 이스케이프_순서() {
        assertEquals("%!!!%%", LikePattern.contains("!%"));
        assertEquals("%!%!_%", LikePattern.contains("%_"));
    }

    @Test
    @DisplayName("% 만 입력하면 모든 제목이 아니라 % 가 든 제목만 찾는 패턴이 된다")
    void 퍼센트만_입력() {
        assertEquals("%!%%", LikePattern.contains("%"));
        assertEquals("%!_%", LikePattern.contains("_"));
    }
}
