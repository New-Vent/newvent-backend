package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 모양 변형(v-*) · 팔레트(palette-*) · 새 블록이 레지스트리 · 정화 · 프롬프트와 맞물리는지 본다.
 */
class VariantPaletteTest {

    private static Element root(String html, String selector) {
        return Jsoup.parseBodyFragment(html).body().selectFirst(selector);
    }

    // ── 레지스트리 ──

    @Test
    @DisplayName("변형 class 이름이 겹치지 않고 전부 v- 로 시작한다")
    void 변형_이름이_유일하다() {
        Set<String> seen = new HashSet<>();
        for (Variant v : Variant.values()) {
            assertTrue(v.cssClass().startsWith(Variant.PREFIX), v + " 가 v- 로 시작하지 않습니다");
            assertTrue(seen.add(v.cssClass()), v.cssClass() + " 가 두 번 나옵니다");
        }
        for (Palette p : Palette.values()) {
            assertTrue(p.cssClass().startsWith(Palette.PREFIX), p + " 가 palette- 로 시작하지 않습니다");
        }
    }

    @Test
    @DisplayName("모델이 만드는 블록은 전부 고를 변형이 있고, 서버 블록은 없다")
    void 블록마다_변형이_있다() {
        for (Block b : Block.llmBlocks()) {
            assertFalse(Variant.of(b).isEmpty(), b.key() + " 에 고를 변형이 없습니다");
        }
        for (Block b : Block.serverBlocks()) {
            assertTrue(Variant.of(b).isEmpty(), b.key() + " 는 서버 소유인데 변형이 있습니다");
        }
    }

    @Test
    @DisplayName("배경 변형은 hero · cta 에는 없다")
    void 배경_변형_범위() {
        assertTrue(Variant.of(Block.HERO).stream().noneMatch(v -> v.group() == Variant.Group.SURFACE));
        assertTrue(Variant.of(Block.CTA).stream().noneMatch(v -> v.group() == Variant.Group.SURFACE));
        assertTrue(Variant.of(Block.FAQ).contains(Variant.SURFACE_DARK));
    }

    // ── 정화 ──

    @Test
    @DisplayName("정화는 그 블록의 변형만 남기고, 묶음마다 하나만 남긴다")
    void 정화가_변형을_거른다() {
        String dirty = "<section data-block=\"benefits\" "
                + "class=\"v-benefits-grid v-benefits-coupon v-hero-left v-made-up v-surface-tint\">"
                + "<ul><li>a</li><li>b</li></ul></section>";

        Element sec = root(BlockValidator.sanitizeGenerated(dirty), "section");

        assertTrue(sec.hasClass("v-benefits-grid"), "먼저 쓴 배치가 남아야 합니다");
        assertFalse(sec.hasClass("v-benefits-coupon"), "같은 묶음의 두 번째는 지워야 합니다");
        assertFalse(sec.hasClass("v-hero-left"), "다른 블록의 변형은 지워야 합니다");
        assertFalse(sec.hasClass("v-made-up"), "목록에 없는 변형은 지워야 합니다");
        assertTrue(sec.hasClass("v-surface-tint"), "배경 묶음은 따로 하나 남아야 합니다");
    }

    @Test
    @DisplayName("정화는 템플릿 class 와 v- 아닌 class 를 건드리지 않는다")
    void 정화가_템플릿_class_를_남긴다() {
        String html = "<section data-block=\"hero\" class=\"ev-block block-hero hl-hero v-nope\">"
                + "<h1 class=\"hero-title\">제목</h1></section>";

        Element sec = root(BlockValidator.sanitizeEdited(html), "section");

        assertTrue(sec.hasClass("ev-block"));
        assertTrue(sec.hasClass("hl-hero"));
        assertFalse(sec.hasClass("v-nope"));
        assertNotNull(root(BlockValidator.sanitizeEdited(html), ".hero-title"));
    }

    @Test
    @DisplayName("팔레트는 hero 섹션에만, 하나만, 아는 것만 남는다")
    void 정화가_팔레트를_거른다() {
        String html = "<section data-block=\"hero\" class=\"palette-autumn palette-night palette-zzz\"><h1>t</h1></section>"
                + "<section data-block=\"cta\" class=\"palette-summer\"><a href=\"#\" class=\"btn\">go</a></section>";

        String out = BlockValidator.sanitizeGenerated(html);

        Element hero = root(out, "[data-block=hero]");
        assertTrue(hero.hasClass("palette-autumn"));
        assertFalse(hero.hasClass("palette-night"), "팔레트는 하나만 남아야 합니다");
        assertFalse(hero.hasClass("palette-zzz"));
        assertFalse(root(out, "[data-block=cta]").hasClass("palette-summer"), "hero 밖의 팔레트는 지워야 합니다");
        assertNotNull(root(out, "a.btn"), "cta 의 btn class 는 그대로여야 합니다");
    }

    @Test
    @DisplayName("변형 안의 요소에 붙은 v- 는 지운다 — 변형은 섹션 루트에만 붙는다")
    void 루트_밖_변형은_지운다() {
        String html = "<section data-block=\"steps\"><ol class=\"v-steps-cards\"><li>a</li><li>b</li></ol></section>";
        assertFalse(BlockValidator.sanitizeGenerated(html).contains("v-steps-cards"));
    }

    // ── 수정 검증 ──

    @Test
    @DisplayName("수정에서 변형 · 팔레트를 바꾸는 건 class_changed 가 아니다")
    void 변형_변경은_허용된다() {
        String before = "<section data-block=\"hero\" class=\"v-hero-center\"><h1>제목</h1><p>소개</p></section>";
        String after = BlockValidator.sanitizeEdited(
                "<section data-block=\"hero\" class=\"v-hero-poster palette-autumn\"><h1>제목</h1><p>소개</p></section>");

        List<BlockValidator.Failure> f = BlockValidator.validateEdited(Block.HERO, before, after);

        assertTrue(f.stream().noneMatch(x -> x.kind() == FailureCode.CLASS_CHANGED), "변형 변경이 막혔습니다: " + f);
    }

    @Test
    @DisplayName("변형 말고 원래 class 를 바꾸면 여전히 class_changed 다")
    void 원래_class_변경은_여전히_막힌다() {
        String before = "<section data-block=\"hero\" class=\"ev-block block-hero\"><h1>제목</h1></section>";
        String after = BlockValidator.sanitizeEdited(
                "<section data-block=\"hero\" class=\"block-hero v-hero-left\"><h1>제목</h1></section>");

        List<BlockValidator.Failure> f = BlockValidator.validateEdited(Block.HERO, before, after);

        assertTrue(f.stream().anyMatch(x -> x.kind() == FailureCode.CLASS_CHANGED), "ev-block 이 빠졌는데 통과했습니다");
    }

    // ── 팔레트 끌어올리기 ──

    @Test
    @DisplayName("저장할 때 hero 의 팔레트가 페이지 루트로 옮겨진다")
    void 팔레트를_루트로_옮긴다() {
        String frag = "<section data-block=\"hero\" class=\"v-hero-left palette-forest\"><h1>t</h1></section>";

        String out = PageShell.plant(frag, null);

        Element rootEl = root(out, ".ev-container");
        assertTrue(rootEl.hasClass("palette-forest"), "루트에 팔레트가 없습니다: " + out);
        assertFalse(root(out, "[data-block=hero]").hasClass("palette-forest"), "hero 에 팔레트가 남았습니다");
        assertTrue(root(out, "[data-block=hero]").hasClass("v-hero-left"), "변형은 hero 에 남아야 합니다");
    }

    @Test
    @DisplayName("새 팔레트가 오면 루트의 옛 팔레트를 바꾸고, base 면 지운다")
    void 팔레트_교체와_되돌리기() {
        String doc = "<div class=\"ev-container event-page theme-holiday palette-summer\">"
                + "<section data-block=\"hero\" class=\"palette-autumn\"><h1>t</h1></section></div>";

        Element r1 = root(PageShell.hoistPalette(doc), ".ev-container");
        assertTrue(r1.hasClass("palette-autumn"));
        assertFalse(r1.hasClass("palette-summer"));
        assertTrue(r1.hasClass("theme-holiday"), "테마는 그대로 둬야 합니다");

        String reset = doc.replace("palette-autumn", "palette-base");
        Element r2 = root(PageShell.hoistPalette(reset), ".ev-container");
        assertTrue(r2.classNames().stream().noneMatch(Palette::looksLike), "base 면 팔레트가 없어야 합니다: " + r2.className());
    }

    @Test
    @DisplayName("팔레트를 안 고른 수정은 루트 팔레트를 건드리지 않는다")
    void 팔레트_없는_수정은_유지() {
        String doc = "<div class=\"ev-container event-page palette-night\">"
                + "<section data-block=\"hero\"><h1>t</h1></section></div>";
        assertTrue(root(PageShell.hoistPalette(doc), ".ev-container").hasClass("palette-night"));
    }

    // ── 블록 순서 ──

    @Test
    @DisplayName("모델이 cta 뒤에 faq 를 내도 저장할 때 레지스트리 순서로 선다 — 유의사항도 cta 바로 앞")
    void 블록_순서를_맞춘다() {
        String frag = "<section data-block=\"cta\"><a href=\"#\" class=\"btn\">go</a></section>"
                + "<section data-block=\"faq\"><dl><dt>q</dt><dd>a</dd><dt>q</dt><dd>a</dd></dl></section>"
                + "<section data-block=\"hero\"><h1>t</h1></section>";

        String out = PageShell.plant(frag, null);

        List<String> order = Jsoup.parseBodyFragment(out).body().select("section[data-block]")
                .eachAttr("data-block");
        assertEquals(List.of("hero", "faq", "notices", "cta"), order, out);
    }

    @Test
    @DisplayName("정렬은 블록 자리만 바꾸고 블록 사이의 다른 노드는 제자리에 둔다")
    void 정렬은_다른_노드를_안_옮긴다() {
        String doc = "<div class=\"ev-container event-page\">"
                + "<section data-block=\"cta\">c</section><div id=\"deco\">d</div>"
                + "<section data-block=\"hero\">h</section><script>x()</script></div>";

        Element root = root(PageShell.settle(doc), ".ev-container");

        assertEquals(List.of("section", "div", "section", "script"),
                root.children().stream().map(Element::normalName).toList());
        assertEquals("hero", root.child(0).attr("data-block"));
        assertEquals("cta", root.child(2).attr("data-block"));
    }

    // ── 새 블록 ──

    @Test
    @DisplayName("새 블록은 선택이라 없어도 생성 검증을 통과하고, 있으면 모양을 본다")
    void 새_블록_검증() {
        String base = "<section data-block=\"hero\"><h1>t</h1><p>x</p></section>"
                + "<section data-block=\"benefits\"><ul><li>a</li><li>b</li></ul></section>"
                + "<section data-block=\"steps\"><ol><li>a</li><li>b</li></ol></section>"
                + "<section data-block=\"cta\"><a href=\"#\" class=\"btn\">go</a></section>";
        assertTrue(BlockValidator.validateGenerated(base).isEmpty());

        String withFaq = base + "<section data-block=\"faq\"><h2>FAQ</h2><dl><dt>q1</dt><dd>a1</dd></dl></section>";
        assertTrue(BlockValidator.validateGenerated(withFaq).stream()
                        .anyMatch(f -> f.code().equals("few_faq")),
                "faq 질문이 1개인데 통과했습니다");
    }

    @Test
    @DisplayName("새 블록은 지울 수 있고, 유의사항은 여전히 cta 바로 앞에 들어간다")
    void 새_블록_조작과_유의사항_자리() {
        for (Block b : List.of(Block.HIGHLIGHT, Block.INTRO, Block.AUDIENCE, Block.FAQ)) {
            assertTrue(b.canDelete() && b.canCreate() && !b.core(), b.key());
        }
        String frag = "<section data-block=\"faq\"><dl><dt>q</dt><dd>a</dd><dt>q</dt><dd>a</dd></dl></section>"
                + "<section data-block=\"cta\"><a href=\"#\" class=\"btn\">go</a></section>";
        String out = PageShell.ensureNotices(frag);
        assertTrue(out.indexOf("data-block=\"faq\"") < out.indexOf("data-block=\"notices\""));
        assertTrue(out.indexOf("data-block=\"notices\"") < out.indexOf("data-block=\"cta\""));
    }

    // ── 프롬프트 ──

    @Test
    @DisplayName("생성 프롬프트에 모든 변형과 팔레트가 나가고, 선택 블록은 따로 안내한다")
    void 생성_프롬프트() {
        String p = PromptBuilder.generate();
        for (Variant v : Variant.values()) assertTrue(p.contains(v.cssClass()), v.cssClass());
        for (Palette pl : Palette.values()) {
            if (pl != Palette.BASE) assertTrue(p.contains(pl.cssClass()), pl.cssClass());
        }
        assertTrue(p.contains("요청문이 그 내용을 직접 말할 때만"));
        int core = p.indexOf("만들 영역:"), optional = p.indexOf("더할 수 있는 영역:");
        assertTrue(p.indexOf("data-block=\"faq\"") > optional && optional > core, "faq 는 선택 영역에 있어야 합니다");
    }

    @Test
    @DisplayName("템플릿 블록 수정에는 변형을 안내하지 않고, hero 에는 팔레트를 안내한다")
    void 수정_프롬프트() {
        assertTrue(PromptBuilder.edit(Block.STEPS, false).contains("v-steps-timeline"));
        assertFalse(PromptBuilder.edit(Block.STEPS, true).contains("v-steps-timeline"));
        assertFalse(PromptBuilder.edit(Block.STEPS, true).contains("모양 고르기"), "고를 게 없으면 절이 없어야 합니다");

        String heroTpl = PromptBuilder.edit(Block.HERO, true);
        assertTrue(heroTpl.contains("palette-autumn"));
        assertFalse(heroTpl.contains("v-hero-left"));
    }

    @Test
    @DisplayName("라우터는 페이지 색감 요청을 hero STYLE 로 보내는 예시를 갖는다")
    void 라우터_예시() {
        assertTrue(PromptBuilder.router().contains("\"op\":\"STYLE\",\"target\":\"hero\""));
    }
}
