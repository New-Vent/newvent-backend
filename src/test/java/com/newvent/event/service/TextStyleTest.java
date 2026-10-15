package com.newvent.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.event.dto.request.TextStyle;
import com.newvent.event.exception.DirectEditException;

/**
 * 제목 글자 크기 · 색 직접 지정.
 *
 * ★★ 왜 모델이 아니라 직접 수정인가 — 글자 크기와 색은 **CSS 가 쥐고 있다.**
 *   모델에게 시키면 class 를 붙이고 "강조했습니다" 라고 보고하는데 화면은 그대로다
 *   (실측: "문구 색을 어둡게" 가 v8 까지 가도 흰색이었다). 돈만 나가고 안 바뀐다.
 *
 * ★ 참여 버튼(ButtonStyle)이 이미 뚫어 둔 길을 제목으로 넓힌 것이다. 같은 수법이다.
 */
class TextStyleTest {

    private static final String PAGE =
            "<div class=\"ev-container event-page\">"
            + "<section data-block=\"hero\"><h1>새로운 시작</h1><h2>보조 제목</h2><p>소개</p></section>"
            + "<section data-block=\"benefits\"><div class=\"block-header\">"
            + "<div class=\"sub-label\">Event Benefits</div><h2 class=\"title\">가입하면 받는 혜택</h2></div>"
            + "<ul><li>커피 쿠폰</li></ul></section>"
            + "<section data-block=\"cta\"><h2>지금 참여</h2>"
            + "<a data-slot=\"cta-link\" href=\"#\">참여하기</a></section></div>";

    private static Document apply(TextStyle style) {
        return Jsoup.parseBodyFragment(DirectEditor.applyTextStyle(PAGE, style));
    }

    // ★ 큰 제목과 소제목을 갈라 받는다. 한 값으로 묶으면 둘의 크기 차이(위계)가 사라진다
    @Test
    @DisplayName("★ 큰 제목만 고르면 소제목은 손대지 않는다")
    void 큰_제목만() {
        Document d = apply(new TextStyle("#15803d", "large", null, null));

        String title = d.selectFirst("[data-block=hero] h1").attr("style");
        assertTrue(title.contains("color: #15803d"), title);
        assertTrue(title.contains("font-size: 46px"), title);

        assertEquals("", d.selectFirst("[data-block=benefits] .title").attr("style"));
        // ★ hero 안의 h2 는 소제목이 아니다 — 큰 제목의 보조다
        assertEquals("", d.selectFirst("[data-block=hero] h2").attr("style"));
    }

    @Test
    @DisplayName("소제목만 고르면 큰 제목은 손대지 않는다")
    void 소제목만() {
        Document d = apply(new TextStyle(null, null, "#0f172a", "small"));

        assertTrue(d.selectFirst("[data-block=benefits] .title").attr("style").contains("font-size: 18px"));
        assertTrue(d.selectFirst("[data-block=cta] h2").attr("style").contains("font-size: 18px"));
        assertEquals("", d.selectFirst("[data-block=hero] h1").attr("style"));
    }

    /**
     * ★★ color 만 박으면 **아무 일도 안 일어나는** 자리가 있다.
     *   v-hero-gradient-text 는 글자를 투명하게 두고 배경을 글자 모양으로 깎아 넣는다.
     *   그때는 -webkit-text-fill-color 가 이긴다 — 95-assets 에서 같은 함정을 겪었다.
     */
    @Test
    @DisplayName("★ 투명 글자 수법을 쓰는 변형도 이긴다")
    void 투명_글자를_이긴다() {
        String style = apply(new TextStyle("#15803d", null, null, null))
                .selectFirst("[data-block=hero] h1").attr("style");

        assertTrue(style.contains("-webkit-text-fill-color: #15803d"), style);
    }

    // ★★ 비움이 "테마를 따른다" 는 뜻이다. 전부 박아 버리면 팔레트를 바꿔도 제목만 옛 색으로 남는다
    @Test
    @DisplayName("★ 비운 항목은 손대지 않는다 — 비움이 \"테마를 따른다\" 다")
    void 비우면_그대로() {
        assertEquals(PAGE, DirectEditor.applyTextStyle(PAGE, new TextStyle(null, null, null, null)));
        assertEquals(PAGE, DirectEditor.applyTextStyle(PAGE, null));
        assertTrue(new TextStyle(" ", "", null, null).isEmpty());
    }

    // ★ 이미 있던 인라인 style 은 지우지 않는다. 덮어쓰면 변형이 건 값이 날아간다
    @Test
    @DisplayName("이미 있는 인라인 style 위에 덧쓴다")
    void 덧쓴다() {
        String html = "<section data-block=\"hero\"><h1 style=\"letter-spacing:-2px\">제목</h1></section>";

        String out = DirectEditor.applyTextStyle(html, new TextStyle("#111111", null, null, null));

        assertTrue(out.contains("letter-spacing: -2px"), out);
        assertTrue(out.contains("color: #111111"), out);
    }

    @Test
    @DisplayName("못 쓰는 값은 거절한다")
    void 못_쓰는_값() {
        assertThrows(DirectEditException.class, () -> new TextStyle("red", null, null, null));
        assertThrows(DirectEditException.class, () -> new TextStyle("#fff", null, null, null));
        assertThrows(DirectEditException.class, () -> new TextStyle(null, "huge", null, null));
        assertEquals("#aabbcc", new TextStyle("#AABBCC", null, null, null).titleColor());
    }

    // ★ 버튼(CTA_NOT_FOUND)과 다르다. 버튼은 반드시 있어야 하지만 제목은 없을 수 있다
    @Test
    @DisplayName("제목이 없는 페이지에서도 터지지 않는다")
    void 제목이_없어도() {
        String html = "<section data-block=\"cta\"><a data-slot=\"cta-link\">참여</a></section>";

        assertEquals(html, DirectEditor.applyTextStyle(
                html, new TextStyle("#111111", "large", "#222222", "large")));
    }

    // ★ 디자인을 고치는 길이다. 문구가 같이 바뀌면 그건 사고다
    @Test
    @DisplayName("★ 문구는 한 글자도 안 바뀐다")
    void 문구는_그대로() {
        Document d = apply(new TextStyle("#15803d", "xlarge", "#0f172a", "small"));

        assertTrue(d.html().contains("새로운 시작"));
        assertTrue(d.html().contains("가입하면 받는 혜택"));
        assertTrue(d.html().contains("커피 쿠폰"));
        assertTrue(d.html().contains("참여하기"));
    }
}
