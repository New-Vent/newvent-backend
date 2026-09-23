package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 모델이 지어낸 날짜를 찾는다. 
 *
 */
class FormValueCheckTest {

    private static List<String> of(String html) {
        return FormValueCheck.leakedDates(html);
    }

    // ── 잡아야 하는 것 ────────────────────────────────────────────

    @Test
    @DisplayName("★ 연도 붙은 날짜")
    void 연월일() {
        assertEquals(List.of("2026.07.01"), of("<p>기간: 2026.07.01 까지</p>"));
        assertEquals(List.of("2026-7-1"),   of("<p>2026-7-1 시작</p>"));
        assertEquals(List.of("2026/07/01"), of("<p>2026/07/01</p>"));
    }

    @Test
    @DisplayName("★ 한글 날짜")
    void 한글날짜() {
        assertEquals(List.of("7월 1일"), of("<p>7월 1일부터 시작합니다</p>"));
        assertEquals(List.of("12월 25일"), of("<section><h2>12월 25일 마감</h2></section>"));
    }

    @Test
    @DisplayName("여러 개면 여러 개 다 잡는다")
    void 여러개() {
        List<String> found = of("<p>2026.07.01 부터 8월 31일 까지</p>");
        assertEquals(2, found.size(), "둘 다 못 잡았습니다: " + found);
    }

    @Test
    @DisplayName("같은 날짜가 두 번 나와도 한 번만 센다")
    void 중복() {
        assertEquals(List.of("2026.07.01"),
                of("<p>2026.07.01</p><p>2026.07.01</p>"));
    }

    // ── 잡으면 안 되는 것 ─────────────────────────────────────────

    @Test
    @DisplayName("★ 슬롯 안의 날짜는 안 본다 — 서버가 채우는 자리다")
    void 슬롯은_제외() {
        assertTrue(of("<span data-slot=\"period\">2026.07.01 ~ 07.31</span>").isEmpty(),
                "슬롯 안을 봤습니다. 슬롯을 채운 뒤에는 항상 걸리게 됩니다.");
    }

    @Test
    @DisplayName("★ 혜택 숫자는 날짜가 아니다")
    void 숫자는_안_잡는다() {
        assertTrue(of("<li>데이터 10GB 추가</li>").isEmpty());
        assertTrue(of("<li>3단계로 참여</li>").isEmpty());
        assertTrue(of("<li>최대 5,000원 할인</li>").isEmpty());
        assertTrue(of("<li>선착순 100명</li>").isEmpty());
        assertTrue(of("<p>1 대 1 문의</p>").isEmpty());
    }

    @Test
    @DisplayName("연도가 없는 숫자 나열은 날짜로 안 본다")
    void 연도가_있어야_한다() {
        assertTrue(of("<p>10.5.3 버전</p>").isEmpty(),
                "연도 없는 숫자를 날짜로 봤습니다. 버전 번호·수치에 다 걸립니다.");
    }

    @Test
    @DisplayName("빈 값에 안 터진다")
    void 빈값() {
        assertTrue(of(null).isEmpty());
        assertTrue(of("").isEmpty());
        assertTrue(of("   ").isEmpty());
    }

    // ── 실제로 생길 법한 모양 ─────────────────────────────────────

    @Test
    @DisplayName("★ 정상 생성 결과는 깨끗하다 — 기간은 슬롯에만 있다")
    void 정상_결과() {
        String html = """
                <section data-block="hero">
                  <h1>여름 데이터 대방출</h1><p>이번 여름 데이터 걱정 없이</p>
                  <p data-slot="period">2026.07.01 ~ 07.31</p>
                </section>
                <section data-block="benefits">
                  <ul><li>데이터 10GB</li><li>스타벅스 쿠폰</li></ul>
                </section>
                """;
        assertTrue(of(html).isEmpty(), "정상 결과에서 오탐이 났습니다: " + of(html));
    }

    @Test
    @DisplayName("★ 모델이 본문에 날짜를 흩뿌린 경우 — 이게 잡으려는 것")
    void 흩뿌린_날짜() {
        String html = """
                <section data-block="hero">
                  <h1>여름 데이터 대방출</h1>
                  <p>2026.07.01 부터 시작!</p>
                  <p data-slot="period"></p>
                </section>
                <section data-block="steps">
                  <ol><li>7월 15일까지 응모</li></ol>
                </section>
                """;
        List<String> found = of(html);
        assertEquals(2, found.size(),
                "본문에 흩뿌린 날짜를 못 잡았습니다: " + found
                + " — 이게 게시되면 폼에서 기간을 고쳐도 이 날짜는 안 바뀝니다.");
    }
}
