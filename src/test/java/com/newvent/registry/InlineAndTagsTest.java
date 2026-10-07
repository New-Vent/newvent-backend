package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JS 없이 동작하는 태그(details · mark · hr · figure)와 문구 꾸밈(t-*), 아코디언 FAQ · 비교표를 본다.
 */
class InlineAndTagsTest {

    private static Element first(String html, String selector) {
        return Jsoup.parseBodyFragment(html).body().selectFirst(selector);
    }

    // ── 허용 태그 ──

    @Test
    @DisplayName("details · summary · mark · hr · figure 는 남고, open 속성도 남는다")
    void 새_태그가_정화를_통과한다() {
        String html = "<section data-block=\"faq\"><div class=\"ev-accordion\">"
                + "<details open><summary>Q</summary><p>A</p></details></div>"
                + "<hr><p><mark>중요</mark></p><figure><figcaption>설명</figcaption></figure></section>";

        String out = BlockValidator.sanitizeGenerated(html);

        for (String tag : List.of("details", "summary", "mark", "hr", "figure", "figcaption")) {
            assertNotNull(first(out, tag), tag + " 가 지워졌습니다: " + out);
        }
        assertTrue(first(out, "details").hasAttr("open"), "open 이 지워졌습니다");
        assertNotNull(first(out, ".ev-accordion"), "아코디언 래퍼 class 가 지워졌습니다");
    }

    @Test
    @DisplayName("JS 가 필요한 태그와 이벤트 속성은 여전히 지운다")
    void 위험한_것은_여전히_지운다() {
        String html = "<section data-block=\"faq\"><details ontoggle=\"alert(1)\"><summary onclick=\"x()\">Q</summary></details>"
                + "<dialog open>d</dialog><button>b</button><script>alert(1)</script></section>";

        String out = BlockValidator.sanitizeGenerated(html);

        assertFalse(out.contains("ontoggle"), out);
        assertFalse(out.contains("onclick"), out);
        assertFalse(out.contains("<dialog"), out);
        assertFalse(out.contains("<button"), out);
        assertFalse(out.contains("<script"), out);
    }

    // ── 문구 꾸밈 ──

    @Test
    @DisplayName("문구 꾸밈은 목록에 있는 것만, 허용 태그에만 남는다")
    void 문구_꾸밈_정화() {
        String html = "<section data-block=\"benefits\" class=\"t-badge\"><ul>"
                + "<li><span class=\"t-badge t-badge-red t-badge-green\">한정</span> 데이터</li>"
                + "<li><strong class=\"t-accent t-made-up\">3GB</strong> 지급</li>"
                + "<li class=\"t-highlight\">항목</li></ul></section>";

        String out = BlockValidator.sanitizeGenerated(html);

        Element badge = first(out, "span");
        assertTrue(badge.hasClass("t-badge") && badge.hasClass("t-badge-red"));
        assertFalse(badge.hasClass("t-badge-green"), "배지 색은 하나만 남아야 합니다");
        assertTrue(first(out, "strong").hasClass("t-accent"));
        assertFalse(first(out, "strong").hasClass("t-made-up"));
        assertFalse(first(out, "li:nth-child(3)").hasClass("t-highlight"), "li 는 허용 태그가 아닙니다");
        assertFalse(first(out, "section").hasClass("t-badge"), "섹션 루트의 t-* 는 지워야 합니다");
    }

    @Test
    @DisplayName("배지 색만 붙이고 t-badge 를 빠뜨리면 서버가 채운다")
    void 배지_바탕을_채운다() {
        String out = BlockValidator.sanitizeGenerated(
                "<section data-block=\"hero\"><h1><span class=\"t-badge-teal\">NEW</span> 제목</h1></section>");
        assertTrue(first(out, "span").hasClass("t-badge"), out);
    }

    @Test
    @DisplayName("긴 문장에 붙은 배지는 떼고 글은 남긴다. 강조(t-accent)는 길어도 둔다")
    void 긴_배지는_뗀다() {
        String out = BlockValidator.sanitizeGenerated("<section data-block=\"intro\"><p>"
                + "<span class=\"t-badge t-badge-red\">본 이벤트는 1인 1회만 참여 가능합니다</span>"
                + "<span class=\"t-badge-green\">무료</span>"
                + "<strong class=\"t-accent\">프리미엄 라운지 이용권 증정</strong></p></section>");

        Element p = first(out, "p");
        Element longOne = p.child(0);
        assertFalse(longOne.hasClass("t-badge") || longOne.hasClass("t-badge-red"), out);
        assertTrue(longOne.text().contains("1인 1회"), "글은 남아야 합니다");
        assertTrue(p.child(1).hasClass("t-badge") && p.child(1).hasClass("t-badge-green"), out);
        assertTrue(p.child(2).hasClass("t-accent"), out);
    }

    // ── 아코디언 FAQ ──

    @Test
    @DisplayName("아코디언 FAQ 도 목록형 FAQ 와 같이 검증을 통과하고, 질문 수를 센다")
    void 아코디언_FAQ_검증() {
        String core = "<section data-block=\"hero\"><h1>t</h1></section>"
                + "<section data-block=\"benefits\"><ul><li>a</li><li>b</li></ul></section>"
                + "<section data-block=\"cta\"><a href=\"#\" class=\"btn\">go</a></section>";
        String ok = core + "<section data-block=\"faq\" class=\"v-faq-accordion\"><div class=\"ev-accordion\">"
                + "<details><summary>q1</summary><p>a1</p></details>"
                + "<details><summary>q2</summary><p>a2</p></details></div></section>";
        String few = core + "<section data-block=\"faq\" class=\"v-faq-accordion\"><div class=\"ev-accordion\">"
                + "<details><summary>q1</summary><p>a1</p></details></div></section>";

        assertTrue(BlockValidator.validateGenerated(ok).isEmpty(), BlockValidator.validateGenerated(ok).toString());
        assertTrue(BlockValidator.validateGenerated(few).stream().anyMatch(f -> f.code().equals("few_faq")));
    }

    @Test
    @DisplayName("목록형 FAQ 를 아코디언으로 바꾸는 수정은 항목이 늘지 않았으면 통과한다")
    void 목록형에서_아코디언으로_바꾸기() {
        String before = "<section data-block=\"faq\"><dl>"
                + "<dt>q1</dt><dd>a1</dd><dt>q2</dt><dd>a2</dd></dl></section>";
        String after = BlockValidator.sanitizeEdited("<section data-block=\"faq\" class=\"v-faq-accordion\">"
                + "<div class=\"ev-accordion\"><details><summary>q1</summary><p>a1</p></details>"
                + "<details><summary>q2</summary><p>a2</p></details></div></section>");

        List<BlockValidator.Failure> f = BlockValidator.validateEdited(Block.FAQ, before, after);
        assertTrue(f.stream().noneMatch(BlockValidator.Failure::isBlocking), f.toString());
    }

    // ── 비교표 ──

    @Test
    @DisplayName("비교표는 행이 2개 이상이어야 하고, 수정에서 행이 늘면 막힌다")
    void 비교표_검증() {
        String table = "<section data-block=\"compare\"><h2>비교</h2><table>"
                + "<tr><th>구분</th><th>A</th></tr><tr><td>데이터</td><td>3GB</td></tr></table></section>";
        String grown = table.replace("</table>", "<tr><td>통화</td><td>무제한</td></tr></table>");

        assertTrue(BlockValidator.validateEdited(Block.COMPARE, table, BlockValidator.sanitizeEdited(table))
                .stream().noneMatch(BlockValidator.Failure::isBlocking));
        assertTrue(BlockValidator.validateEdited(Block.COMPARE, table, BlockValidator.sanitizeEdited(grown))
                .stream().anyMatch(f -> f.kind() == FailureCode.ITEM_ADDED), "행이 늘었는데 통과했습니다");
    }

    // ── 프롬프트 ──

    @Test
    @DisplayName("생성 · 수정 프롬프트에 문구 꾸밈이 모두 나가고, 아코디언 마크업을 안내한다")
    void 프롬프트() {
        String gen = PromptBuilder.generate();
        String edit = PromptBuilder.edit(Block.BENEFITS, true);
        for (Inline i : Inline.values()) {
            assertTrue(gen.contains(i.cssClass()), i.cssClass());
            assertTrue(edit.contains(i.cssClass()), "템플릿 블록 수정에도 문구 꾸밈은 안내해야 합니다: " + i.cssClass());
        }
        // 생성 프롬프트는 변형을 표본으로만 싣는다 — 아코디언 마크업 안내는 FAQ 수정 프롬프트에 늘 있다
        assertTrue(PromptBuilder.edit(Block.FAQ).contains("<details><summary>질문</summary><p>답</p></details>"));
        assertTrue(gen.contains("data-block=\"compare\""));
    }

    @Test
    @DisplayName("생성 프롬프트의 블록 묶음 제목에 대괄호를 쓰지 않는다 — 검증기가 자리표시자로 본다")
    void 프롬프트_목록에_대괄호_제목이_없다() {
        String gen = PromptBuilder.generate();
        for (Block b : Block.llmBlocks()) {
            assertFalse(gen.contains("[" + b.key() + "]"), b.key() + " 를 대괄호로 묶었습니다");
        }
    }
}
