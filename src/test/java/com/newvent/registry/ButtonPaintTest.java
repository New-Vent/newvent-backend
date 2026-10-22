package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ButtonPaintTest {

    private static final String DARK_CTA = "<section data-block=\"cta\" class=\"v-cta-dark\">"
            + "<a class=\"btn\" data-slot=\"cta-link\" href=\"#\">참여하기</a></section>";

    private static Element button(String html) {
        return Jsoup.parseBodyFragment(html).selectFirst("[data-slot=cta-link]");
    }

    @Test
    @DisplayName("★ 실측 문장 — '배경이 아니라 버튼 자체를 검정색으로' 는 버튼을 칠한다")
    void 배경이_아니라_버튼을_칠한다() {
        ButtonPaint p = ButtonPaint.requestedBy("참여버튼 배경이 아니라, 참여버튼 자체를 검정색으로 바꿔줘").orElseThrow();

        assertEquals("#111111", p.hex());
        assertFalse(p.textOnly());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"참여 영역 배경을 검정으로", "전체 색감을 파란색으로", "참여 버튼 모양 바꿔줘", "버튼을 크게"})
    @DisplayName("영역 배경 · 페이지 전체 색 · 색 없는 요청은 버튼 칠하기가 아니다")
    void 버튼_색이_아닌_요청(String request) {
        assertTrue(ButtonPaint.requestedBy(request).isEmpty(), request);
    }

    @Test
    @DisplayName("색이 둘이면 마지막 것 — '파란 버튼을 빨간색으로' 의 목표는 빨강")
    void 마지막_색이_목표다() {
        assertEquals("#e03131", ButtonPaint.requestedBy("파란 버튼을 빨간색으로 바꿔줘").orElseThrow().hex());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"참여 버튼은 검정으로, 혜택 배경은 노랑으로", "혜택 배경은 노랑으로, 참여 버튼은 검정으로",
            "제목은 빨강으로 바꾸고 버튼은 검정으로 해줘"})
    @DisplayName("★ 리뷰 — 다른 영역의 색은 버튼 몫이 아니다. 버튼을 말한 구절의 색만 고른다")
    void 버튼을_말한_구절의_색만_고른다(String request) {
        assertEquals("#111111", ButtonPaint.requestedBy(request).orElseThrow().hex(), request);
    }

    @Test
    @DisplayName("★ 리뷰 — '텍스트는 지금 참여로' 는 문구 변경이다. 배경색 요청을 글자색으로 바꾸지 않는다")
    void 문구_변경은_글자색이_아니다() {
        ButtonPaint p = ButtonPaint.requestedBy("버튼을 검정으로 바꾸고 텍스트는 지금 참여로 변경해줘").orElseThrow();

        assertFalse(p.textOnly(), "배경색 요청이다");
        assertEquals("#111111", p.hex());
        assertEquals(null, p.textHex());
    }

    @Test
    @DisplayName("배경색과 글자색을 같이 말하면 둘 다 칠한다 — '버튼은 검정, 글자는 흰색으로'")
    void 배경과_글자를_같이_칠한다() {
        ButtonPaint p = ButtonPaint.requestedBy("참여 버튼은 검정, 글자는 노란색으로").orElseThrow();

        String style = button(p.apply(DARK_CTA)).attr("style");
        assertTrue(style.contains("background-color:#111111"), style);
        assertTrue(style.contains("color:#fcc419"), "정한 글자색이 밝기 자동값보다 먼저다: " + style);
    }

    @Test
    @DisplayName("후속 요청은 직전 요청에서 색만 빌린다 — '제목을 빨간색으로' 의 빨강이 버튼 색이 된다")
    void 직전_요청에서_색만_빌린다() {
        assertTrue(ButtonPaint.requestedBy("제목을 빨간색으로 바꿔줘").isEmpty(), "제목 구절은 버튼 몫이 아니다");
        assertEquals("#e03131", ButtonPaint.carriedFrom("제목을 빨간색으로 바꿔줘").orElseThrow().hex());
        assertTrue(ButtonPaint.carriedFrom("제목 글자를 노란색으로").orElseThrow().textOnly());
        assertTrue(ButtonPaint.carriedFrom("제목을 크게 바꿔줘").isEmpty(), "색이 없으면 빌릴 게 없다");
    }

    @Test
    @DisplayName("대상이 없는 뒤 구절은 앞 구절(버튼)을 잇는다 — '참여 버튼 크게, 노란색으로'")
    void 대상_없는_구절은_앞을_잇는다() {
        assertEquals("#fcc419", ButtonPaint.requestedBy("참여 버튼 크게, 노란색으로").orElseThrow().hex());
    }

    @Test
    @DisplayName("버튼 색 — 배경색 · 그라데이션 끄기 · 읽히는 글자색을 같이 칠하고 어두운 상자는 걷는다")
    void 버튼을_칠한다() {
        String out = ButtonPaint.requestedBy("참여 버튼을 검정색으로").orElseThrow().apply(DARK_CTA);

        String style = button(out).attr("style");
        assertTrue(style.contains("background-color:#111111"), style);
        assertTrue(style.contains("background-image:none"), "그라데이션 버튼이면 색이 안 보인다: " + style);
        assertTrue(style.contains("color:#ffffff"), "검은 버튼에 검은 글자면 안 보인다: " + style);
        assertFalse(out.contains("v-cta-dark"), "검은 버튼을 검은 상자에 두면 버튼이 사라진다");
    }

    @Test
    @DisplayName("밝은 버튼이면 어두운 상자를 그대로 둔다 — 그 상자는 원래 밝은 버튼용이다")
    void 밝은_버튼은_상자를_둔다() {
        String out = ButtonPaint.requestedBy("참여 버튼을 노란색으로").orElseThrow().apply(DARK_CTA);

        assertTrue(out.contains("v-cta-dark"));
        assertTrue(button(out).attr("style").contains("color:#111111"), "노란 버튼에는 어두운 글자");
    }

    @Test
    @DisplayName("'버튼 글자를 노란색으로' 는 글자색만 바꾼다")
    void 글자색만_바꾼다() {
        String out = ButtonPaint.requestedBy("버튼 글자를 노란색으로").orElseThrow().apply(DARK_CTA);

        String style = button(out).attr("style");
        assertEquals("color:#fcc419;", style);
    }

    @Test
    @DisplayName("data-slot 이 없는 옛 페이지 — cta 블록의 버튼을 칠한다")
    void 슬롯_없는_버튼도_칠한다() {
        String old = "<section data-block=\"cta\"><button class=\"btn\">참여하기</button></section>";
        ButtonPaint p = ButtonPaint.requestedBy("버튼을 파란색으로").orElseThrow();

        assertTrue(p.fits(old));
        assertTrue(p.apply(old).contains("background-color:#1c7ed6"));
        assertFalse(p.fits("<section data-block=\"hero\"><h1>제목</h1></section>"), "cta 가 없으면 칠할 곳이 없다");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"참여버튼을 검정색으로 바꿔줘", "참여버튼 배경이 아니라, 참여버튼 자체를 검정색으로 바꿔줘",
            "버튼 색 좀 더 진한 빨강으로 해주세요", "참여 버튼도 그렇게 바꿔줘"})
    @DisplayName("색 말고 시킨 게 없으면 모델을 부르지 않는다")
    void 색만_시킨_요청(String request) {
        assertTrue(ButtonPaint.onlyPaint(request), request);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"버튼을 검정 알약 모양으로", "버튼을 검정으로 하고 문구는 지금 참여로", "검정 버튼에 반짝이는 효과"})
    @DisplayName("색 말고 시킨 게 있으면 모델도 부른다")
    void 다른_것도_시킨_요청(String request) {
        assertFalse(ButtonPaint.onlyPaint(request), request);
    }

    @Test
    @DisplayName("색 이름 묶음마다 칠할 색이 있다 — 묶음을 늘리고 색을 안 넣으면 여기서 깨진다")
    void 모든_색에_값이_있다() {
        for (ColorWords.Group g : ColorWords.GROUPS) {
            String w = g.words().get(0);
            assertNotNull(ButtonPaint.requestedBy("버튼을 " + w + "으로").orElseThrow().hex(), g.name());
        }
    }

    @Test
    @DisplayName("정화는 background-image 를 none 일 때만 남긴다 — url() 로 바깥 이미지를 못 끌어온다")
    void 정화는_none_만_남긴다() {
        String kept = BlockValidator.sanitizeEdited(
                "<section data-block=\"cta\"><a data-slot=\"cta-link\" style=\"background-image:none;color:#fff\">참여</a></section>");
        String dropped = BlockValidator.sanitizeEdited(
                "<section data-block=\"cta\"><a data-slot=\"cta-link\" style=\"background-image:linear-gradient(red,blue)\">참여</a></section>");

        assertTrue(kept.contains("background-image:none"), kept);
        assertFalse(dropped.contains("background-image"), dropped);
    }
}
