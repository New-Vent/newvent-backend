package com.newvent.editor.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 라우터 출력 파싱. **레지스트리도 스프링도 모델도 안 쓴다.**
 *
 * 여기서 보는 건 "글자 → List&lt;RawRoute&gt;" 하나뿐이다.
 * op 이 말이 되는지, target 이 있는 블록인지는 관문(Gate) 테스트의 일이다.
 */
class RouteParserTest {

    private static List<RawRoute> ok(String raw) {
        return RouteParser.parse(raw).orElseThrow(
                () -> new AssertionError("파싱에 실패했습니다: " + raw));
    }

    private static void fails(String raw, String why) {
        assertTrue(RouteParser.parse(raw).isEmpty(), why);
    }

    // ── 정상 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("ops 배열을 순서대로 읽는다")
    void 배열() {
        List<RawRoute> ops = ok("""
                {"ops":[{"op":"EDIT","target":"hero","content":"가을 대축제"},
                        {"op":"ADD","target":"benefits","content":null}]}
                """);

        assertEquals(2, ops.size());
        assertEquals("EDIT", ops.get(0).op());
        assertEquals("hero", ops.get(0).target());
        assertEquals("가을 대축제", ops.get(0).content());
        assertEquals("ADD", ops.get(1).op());
        assertNull(ops.get(1).content());
    }

    @Test
    @DisplayName("★ 코드펜스와 설명이 붙어 와도 읽는다 — 생성에서 45% 확률로 붙었다")
    void 군더더기() {
        List<RawRoute> ops = ok("""
                분류 결과는 다음과 같습니다:
                ```json
                {"ops":[{"op":"EDIT","target":"benefits","content":null}]}
                ```
                혜택 영역을 수정하면 됩니다.
                """);

        assertEquals(1, ops.size());
        assertEquals("benefits", ops.get(0).target());
    }

    @Test
    @DisplayName("래퍼를 빼먹고 배열만 뱉어도 읽는다")
    void 래퍼_없음() {
        List<RawRoute> ops = ok("""
                [{"op":"EDIT","target":"cta","content":"지금 참여하기"}]
                """);

        assertEquals(1, ops.size());
        assertEquals("지금 참여하기", ops.get(0).content());
    }

    @Test
    @DisplayName("옛 단수 형태도 하나짜리로 읽는다")
    void 단수_형태() {
        List<RawRoute> ops = ok("""
                {"op":"DELETE","target":"steps"}
                """);

        assertEquals(1, ops.size());
        assertEquals("DELETE", ops.get(0).op());
        assertNull(ops.get(0).content(), "없는 필드는 null 이어야 합니다.");
    }

    // ── null 처리 ─────────────────────────────────────────────────

    @Test
    @DisplayName("★ 글자 \"null\" 도 자바 null 로 만든다 — 모델이 예시를 따옴표째 베낀다")
    void 글자_null() {
        List<RawRoute> ops = ok("""
                {"ops":[{"op":"EDIT","target":"hero","content":"null"}]}
                """);

        assertNull(ops.get(0).content(),
                "글자 \"null\" 이 그대로 남으면, 내용이 없는데 있는 것처럼 보여서 "
                + "되물어야 할 자리에서 안 되묻습니다.");
    }

    @Test
    @DisplayName("빈 문자열도 null 이다")
    void 빈_문자열() {
        List<RawRoute> ops = ok("""
                {"ops":[{"op":"EDIT","target":"hero","content":"   "}]}
                """);
        assertNull(ops.get(0).content());
    }

    // ── 실패 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("★ 최상위 JSON 이 두 개면 실패한다 — 앞의 것만 쓰면 시킨 절반이 사라진다")
    void 최상위_두_개() {
        fails("""
                {"ops":[{"op":"EDIT","target":"hero"}]}
                {"ops":[{"op":"EDIT","target":"benefits"}]}
                """,
                "앞의 것만 조용히 골랐습니다. 둘 중 무엇을 의도했는지 알 방법이 없습니다.");
    }

    @Test
    @DisplayName("★ 빈 배열은 실패다")
    void 빈_배열() {
        fails("{\"ops\":[]}",
                "성공인데 아무 일도 안 일어나는 상태를 호출부가 다뤄야 합니다.");
    }

    @Test
    @DisplayName("★ MAX_OPS 를 넘으면 자르지 않고 실패한다")
    void 너무_많음() {
        StringBuilder b = new StringBuilder("{\"ops\":[");
        for (int i = 0; i <= RouteParser.MAX_OPS; i++) {
            if (i > 0) b.append(',');
            b.append("{\"op\":\"EDIT\",\"target\":\"hero\"}");
        }
        b.append("]}");

        fails(b.toString(),
                "앞의 " + RouteParser.MAX_OPS + "개만 쓰면 관리자가 시킨 것의 일부가 사라집니다.");
    }

    @Test
    @DisplayName("JSON 이 아니면 예외가 아니라 빈 값이다")
    void 쓰레기() {
        fails("무슨 말인지 모르겠습니다", "괄호가 없습니다.");
        fails("{이건 JSON 이 아니다}", "괄호는 맞았지만 JSON 이 아닙니다.");
        fails(null, "null 입력에 예외가 나면 호출부가 try-catch 로 감싸게 됩니다.");
        fails("", "빈 문자열");
    }

    @Test
    @DisplayName("배열 원소가 객체가 아니면 실패한다")
    void 원소가_객체가_아님() {
        fails("{\"ops\":[\"EDIT\"]}", "문자열이 들어왔습니다.");
    }

    @Test
    @DisplayName("모르는 op·target 이 와도 예외가 아니다 — 판단은 관문이 한다")
    void 모르는_값도_담는다() {
        List<RawRoute> ops = ok("""
                {"ops":[{"op":"수정","target":"header","content":null}]}
                """);

        assertEquals("수정", ops.get(0).op(),
                "파서가 값을 검사하면 관문의 일을 뺏고, 파서 테스트에 레지스트리가 끌려옵니다.");
        assertEquals("header", ops.get(0).target());
    }

    @Test
    @DisplayName("Op.find 는 모르는 값에 빈 값을 준다")
    void op_찾기() {
        assertEquals(Optional.of(Op.EDIT), Op.find("edit"));
        assertEquals(Optional.of(Op.ADD), Op.find("  ADD  "));
        assertTrue(Op.find("수정").isEmpty());
        assertTrue(Op.find(null).isEmpty());
        assertTrue(Op.find("ADD_ITEM").isEmpty(),
                "ADD_ITEM 은 일부러 안 넣었습니다. v11 에서 op 정확도가 4/12 였습니다.");
    }
}
