package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EditResponseParserTest {

    private static final String HTML =
        "<section data-block=\"hero\"><h1>가을 축제</h1></section>";

    @Test
    void HTML과_변경요약을_분리한다() {
        var parsed = EditResponseParser.parse(
            HTML + "\n<!-- changeSummary: 제목 변경 -->"
        ).orElseThrow();

        assertEquals(HTML, parsed.html());
        assertEquals("제목 변경", parsed.changeSummary());
    }

    @Test
    void 요약이_없어도_HTML을_유지한다() {
        var parsed = EditResponseParser.parse(HTML).orElseThrow();

        assertEquals(HTML, parsed.html());
        assertNull(parsed.changeSummary());
    }

    @Test
    void 요약이_너무_길어도_HTML을_유지한다() {
        var parsed = EditResponseParser.parse(
            HTML + "\n<!-- changeSummary: " + "가".repeat(101) + " -->"
        ).orElseThrow();

        assertEquals(HTML, parsed.html());
        assertNull(parsed.changeSummary());
    }

    @Test
    void 여러줄_요약은_한줄로_정리한다() {
        var parsed = EditResponseParser.parse(
            HTML + "\n<!-- changeSummary: 제목 변경\n문구 변경 -->"
        ).orElseThrow();

        assertEquals("제목 변경 문구 변경", parsed.changeSummary());
    }

    @Test
    void HTML_코드블록도_허용한다() {
        var parsed = EditResponseParser.parse(
            "```html\n" + HTML
                + "\n<!-- changeSummary: 제목 변경 -->\n```"
        ).orElseThrow();

        assertEquals(HTML, parsed.html());
        assertEquals("제목 변경", parsed.changeSummary());
    }

    @Test
    void HTML_없이_요약만_있으면_거부한다() {
        assertTrue(EditResponseParser.parse(
            "<!-- changeSummary: 제목 변경 -->"
        ).isEmpty());
    }

    @Test
    void 빈_응답은_거부한다() {
        assertTrue(EditResponseParser.parse(null).isEmpty());
        assertTrue(EditResponseParser.parse(" ").isEmpty());
    }
}
