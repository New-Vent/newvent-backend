package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EditResponseParserTest {

    @Test
    void 정상_JSON에서_HTML과_변경요약을_추출한다() {
        String raw = """
                {
                  "html": "<section data-block=\\"hero\\"><h1>가을 축제</h1></section>",
                  "changeSummary": "제목을 가을 축제로 변경"
                }
                """;

        var parsed = EditResponseParser.parse(raw).orElseThrow();

        assertEquals(
            "<section data-block=\"hero\"><h1>가을 축제</h1></section>",
            parsed.html());
        assertEquals("제목을 가을 축제로 변경", parsed.changeSummary());
    }

    @Test
    void JSON_코드블록으로_감싼_응답도_파싱한다() {
        String raw = """
                ```json
                {"html":"<section>내용</section>","changeSummary":"문구 변경"}
                ```
                """;

        var parsed = EditResponseParser.parse(raw).orElseThrow();

        assertEquals("<section>내용</section>", parsed.html());
        assertEquals("문구 변경", parsed.changeSummary());
    }

    @Test
    void 변경요약의_앞뒤_공백을_제거한다() {
        var parsed = EditResponseParser.parse(response("  제목 변경  ")).orElseThrow();
        assertEquals("제목 변경", parsed.changeSummary());
    }

    @Test
    void null이나_빈_응답은_거부한다() {
        assertTrue(EditResponseParser.parse(null).isEmpty());
        assertTrue(EditResponseParser.parse("").isEmpty());
        assertTrue(EditResponseParser.parse("   ").isEmpty());
    }

    @Test
    void JSON이_아닌_HTML만_반환하면_거부한다() {
        assertTrue(EditResponseParser.parse("<section>수정한 내용</section>").isEmpty());
    }

    @Test
    void JSON_객체가_아니면_거부한다() {
        assertTrue(EditResponseParser.parse("[]").isEmpty());
        assertTrue(EditResponseParser.parse("null").isEmpty());
    }

    @Test
    void 필수_필드가_없으면_거부한다() {
        assertTrue(EditResponseParser.parse("{\"html\":\"<section>내용</section>\"}").isEmpty());
        assertTrue(EditResponseParser.parse("{\"changeSummary\":\"문구 변경\"}").isEmpty());
    }

    @Test
    void 필드가_문자열이_아니면_거부한다() {
        assertTrue(EditResponseParser.parse("{\"html\":123,\"changeSummary\":\"문구 변경\"}").isEmpty());

        assertTrue(EditResponseParser.parse(
                "{\"html\":\"<section>내용</section>\",\"changeSummary\":123}")
            .isEmpty());

        assertTrue(EditResponseParser.parse(
                "{\"html\":\"<section>내용</section>\",\"changeSummary\":null}")
            .isEmpty());
    }

    @Test
    void HTML이나_변경요약이_비어있으면_거부한다() {
        assertTrue(EditResponseParser.parse("{\"html\":\" \",\"changeSummary\":\"문구 변경\"}").isEmpty());

        assertTrue(EditResponseParser.parse(response("")).isEmpty());
        assertTrue(EditResponseParser.parse(response("   ")).isEmpty());
    }

    @Test
    void 여러줄_변경요약은_거부한다() {
        // JSON의 \\n과 \\r은 파싱 후 실제 줄바꿈 문자가 된다.
        assertTrue(EditResponseParser.parse(response("제목 변경\\n버튼 변경")).isEmpty());
        assertTrue(EditResponseParser.parse(response("제목 변경\\r버튼 변경")).isEmpty());
    }

    @Test
    void 변경요약은_100자까지_허용한다() {
        String summary = "가".repeat(100);

        var parsed = EditResponseParser.parse(response(summary)).orElseThrow();
        assertEquals(summary, parsed.changeSummary());
    }

    @Test
    void 변경요약이_100자를_초과하면_거부한다() {
        assertTrue(EditResponseParser.parse(response("가".repeat(101))).isEmpty());
    }

    @Test
    void 잘못된_JSON은_거부한다() {
        assertTrue(EditResponseParser.parse("{\"html\":\"내용\",\"changeSummary\":}").isEmpty());
    }

    @Test
    void JSON_뒤에_다른_객체나_설명이_붙으면_거부한다() {
        String valid = response("문구 변경");

        assertTrue(EditResponseParser.parse(valid + "\n{\"extra\":true}").isEmpty());

        assertTrue(EditResponseParser.parse(valid + "\n수정 완료했습니다.").isEmpty());
    }

    /**
     * 테스트용 JSON을 만든다.
     * summary에는 일반 문자열 또는 JSON 이스케이프 문자열을 전달한다.
     */
    private static String response(String summary) {
        return """
                {"html":"<section>내용</section>","changeSummary":"%s"}
                """.formatted(summary);
    }
}
