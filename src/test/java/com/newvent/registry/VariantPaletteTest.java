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

    /**
     * ★ 예전에는 "모든 변형이 생성 프롬프트에 나간다" 였다. 변형이 111개로 늘면서
     *   전부 실으면 생성 입력이 두 배가 된다. 그래서 생성에는 블록마다
     *   {@link Variant#sample} 로 고른 몇 개만 싣는다 — 기본(목록 첫 번째)은 빼고.
     *
     *   <b>대신 "모든 변형이 어딘가로는 나간다" 는 지킨다</b> — 표본에서 빠진 것은
     *   수정 프롬프트에 나간다. 수정은 블록 하나치라 전부 실어도 싸고,
     *   모양을 조목조목 고르는 일이 거기서 일어난다.
     *   그 대조는 {@code 모든_변형이_어딘가로는_나간다} 가 본다.
     */
    @Test
    @DisplayName("생성 프롬프트에 변형 표본과 팔레트가 나가고, 선택 블록은 따로 안내한다")
    void 생성_프롬프트() {
        String p = PromptBuilder.generate();
        // 변형은 블록마다 기본을 뺀 몇 개만 싣는다 (아래 표본 테스트). 기본은 class 를 안 붙이면 나온다고 안내한다
        assertTrue(p.contains("붙이지 않으면 평범한 기본 모양"));
        for (Palette pl : Palette.values()) {
            if (pl != Palette.BASE) assertTrue(p.contains(pl.cssClass()), pl.cssClass());
        }
        assertTrue(p.contains("요청문이 그 내용을 직접 말할 때만"));

        // ★ 블록 목록이 세 묶음이다 — 만들 영역(core) · 이번 요청에 꼭 넣을 영역(열쇠말이 걸린 것) ·
        //   더할 수 있는 영역(판단이 남은 것). faq 는 열쇠말이 있는 블록이라,
        //   요청문을 안 주면(= 거르지 않으면) 둘째 묶음에 들어간다.
        //   중요한 건 "core 묶음에는 없다" 이므로 첫 선택 묶음 뒤에 있는지를 본다.
        int core = p.indexOf("만들 영역:");
        int triggered = p.indexOf("이번 요청에 꼭 넣을 영역:");
        int optional = p.indexOf("더할 수 있는 영역:");
        int firstOptionalSection = triggered > 0 ? triggered : optional;
        assertTrue(firstOptionalSection > core, "선택 묶음은 필수 묶음 뒤에 와야 합니다");
        assertTrue(p.indexOf("data-block=\"faq\"") > firstOptionalSection,
                "faq 는 '만들 영역'(항상 만드는 묶음)에 있으면 안 됩니다");
    }

    /**
     * 변형이 레지스트리에만 있고 어느 프롬프트에도 안 나가면 <b>죽은 이름</b>이다 —
     * event.css 에 규칙까지 써 두고 아무도 못 쓰는 상태가 된다. 실제로 그런 게 59개 있었다.
     *
     * ★ 생성에는 표본만 나간다. 표본에서 빠진 것은 수정 프롬프트에 나간다.
     *   어느 쪽에도 없으면 실패다.
     * ★ SURFACE 변형은 블록 전용이 아니므로 {@link Variant#of} 가 주는 블록 아무거나에서 찾는다.
     */
    @Test
    @DisplayName("★ 모든 변형이 생성이든 수정이든 어딘가로는 나간다 — 죽은 이름이 없다")
    void 모든_변형이_어딘가로는_나간다() {
        String gen = PromptBuilder.generate();
        for (Variant v : Variant.values()) {
            if (gen.contains(v.cssClass())) continue;
            boolean inEdit = Block.llmBlocks().stream()
                    .filter(b -> Variant.of(b).contains(v))
                    .anyMatch(b -> PromptBuilder.edit(b, false).contains(v.cssClass()));
            assertTrue(inEdit,
                    v.cssClass() + " 가 생성에도 수정에도 안 나갑니다. "
                    + "레지스트리에만 있는 이름은 모델이 영영 못 고릅니다");
        }
    }

    /**
     * ★ 생성 프롬프트가 길어지면 백지 생성 1회 비용이 바로 오른다.
     *   상한을 숫자로 박아 둔다 — 블록당 표본 수를 올리거나 shape 를 늘리면 여기서 걸린다.
     *
     * ★ 두 가지를 같이 본다 — 보통 요청과 <b>최악</b>.
     *   선택 블록(stats · prize · coupon · schedule · faq · compare · audience)은
     *   요청문에 열쇠말이 있을 때만 목록에 들어간다. 그래서 보통 요청은 짧고,
     *   열쇠말이 다 걸린 요청이 제일 길다. <b>한쪽만 재면 늘어난 걸 놓친다.</b>
     *
     *   {@code generate(null)} 은 거르지 않으므로 그 자체가 최악이다(테스트 · 내부용).
     *   오늘 기준 보통 7,355자 · 최악 9,268자다.
     */
    @Test
    @DisplayName("생성 프롬프트가 너무 길어지지 않는다 — 비용 상한")
    void 생성_프롬프트_길이_상한() {
        int plain = PromptBuilder.generate("데이터 3GB 를 주는 신규 가입 이벤트").length();
        assertTrue(plain < 9000,
                "보통 요청의 생성 프롬프트가 " + plain + "자입니다. 9,000자를 넘기면 "
                + "생성 1회 비용이 눈에 띄게 오릅니다. "
                + "PromptBuilder.GENERATE_LAYOUTS_PER_BLOCK 를 줄이는 걸 먼저 검토하세요");

        int worst = PromptBuilder.generate().length();
        assertTrue(worst < 13000,
                "모든 블록이 걸린 생성 프롬프트가 " + worst + "자입니다. 13,000자를 넘으면 "
                + "블록을 늘리기 전에 블록당 표본 수를 먼저 줄이세요");
    }

    @Test
    @DisplayName("생성 프롬프트의 변형 표본 — 블록마다 최대 4개, 같은 요청문이면 같고, 요청문을 바꾸면 전부 한 번은 나온다")
    void 변형_표본() {
        for (Block b : Block.llmBlocks()) {
            List<Variant> layouts = Variant.of(b).stream().filter(v -> v.group() == Variant.Group.LAYOUT).toList();
            if (layouts.isEmpty()) continue;

            List<Variant> one = Variant.sample(b, "가을 이벤트", 4);
            assertTrue(one.size() <= 4, b.key());
            // 기본을 실었더니 모델이 매번 그걸 골랐다 (Bedrock 실측) — 기본은 빼고 싣는다
            assertFalse(one.contains(layouts.get(0)), b.key() + " 기본 변형이 실렸습니다");
            assertEquals(one, Variant.sample(b, "가을 이벤트", 4), "같은 요청문인데 목록이 바뀌었습니다 — 재시도가 흔들립니다");

            Set<Variant> seen = new HashSet<>();
            for (int i = 0; i < 300; i++) seen.addAll(Variant.sample(b, "요청 " + i, 4));
            assertEquals(new HashSet<>(layouts.subList(1, layouts.size())), seen,
                    b.key() + " 의 변형 중 한 번도 안 실리는 것이 있습니다");
        }
        assertTrue(Variant.sampleSurfaces("x", 4).size() <= 4);
        assertFalse(Variant.sampleSurfaces("x", 4).contains(Variant.SURFACE_CARD));
    }

    @Test
    @DisplayName("색 지정 배경 — 수정 프롬프트에만 실리고, 생성 표본에는 안 실리며, 다른 배경과 하나만 남는다")
    void 색_지정_배경() {
        List<Variant> colors = List.of(Variant.values()).stream().filter(Variant::namedColor).toList();
        assertFalse(colors.isEmpty());
        for (Variant v : colors) {
            assertEquals(Variant.Group.SURFACE, v.group(), v + " 는 배경 묶음이어야 다른 배경을 대신한다");
        }

        // 수정 — 배경을 쓰는 블록에는 색 배경과 "색 이름으로 고르라" 는 안내가 실린다
        String intro = PromptBuilder.edit(Block.INTRO, false);
        assertTrue(intro.contains("v-surface-blue") && intro.contains("파란색"), intro);
        assertTrue(intro.contains("그 색 이름이 적힌 것을 고른다"));
        // 배경이 없는 블록에는 안내도 없다 — 목록 없이 말만 하면 모델이 이름을 지어낸다
        assertFalse(PromptBuilder.edit(Block.CTA, false).contains("그 색 이름이"));

        // 생성 — 팔레트와 따로 노는 색은 섞지 않는다
        for (int i = 0; i < 300; i++) {
            assertTrue(Variant.sampleSurfaces("요청 " + i, 4).stream().noneMatch(Variant::namedColor));
        }

        // 정화 — 원래 배경을 남긴 채 파란색을 붙이면 앞의 것 하나만 남는다
        Element sec = root("<section data-block=\"intro\" class=\"v-surface-blue v-surface-tint\"><p>x</p></section>",
                "section");
        Variant.sanitize(sec, Block.INTRO);
        assertEquals(Set.of("v-surface-blue"), sec.classNames());
    }

    @Test
    @DisplayName("새 블록(stats · prize · coupon · schedule)도 변형과 배경을 갖고, prize 는 항목이 폼 값이다")
    void 새_블록_레지스트리() {
        for (Block b : List.of(Block.STATS, Block.PRIZE, Block.COUPON, Block.SCHEDULE)) {
            assertTrue(Variant.of(b).stream().anyMatch(v -> v.group() == Variant.Group.LAYOUT), b.key());
            assertFalse(b.core(), b.key() + " 는 선택 블록이어야 합니다");
        }
        for (Block b : List.of(Block.STATS, Block.PRIZE, Block.SCHEDULE)) {
            assertTrue(Variant.of(b).contains(Variant.SURFACE_GLASS), b.key() + " 에 배경 변형이 없습니다");
        }
        // 쿠폰은 그라데이션 바탕 + 흰 글씨가 디자인 — 배경 변형이 붙으면 빈 상자가 된다 (Bedrock 실측)
        assertTrue(Variant.of(Block.COUPON).stream().noneMatch(v -> v.group() == Variant.Group.SURFACE));
        String cleaned = BlockValidator.sanitizeGenerated(
                "<section data-block=\"coupon\" class=\"v-coupon-ticket v-surface-card\"><p>쿠폰</p></section>");
        assertFalse(cleaned.contains("v-surface-card"), "쿠폰에 붙은 배경 변형을 지워야 합니다: " + cleaned);
        assertTrue(Block.PRIZE.itemsAreFormValues(), "경품은 관리자가 정하는 값이라 항목 추가를 막아야 합니다");
        assertFalse(Block.STATS.itemsAreFormValues());
        assertDoesNotThrow(Block::assertConsistent);
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
    @DisplayName("영역을 골라 고칠 때는 hero 에도 페이지 전체 팔레트를 안내하지 않는다 — 선택 밖 영역의 색이 바뀐다")
    void 선택_영역_수정은_팔레트_안내_없음() {
        String chosen = PromptBuilder.edit(Block.HERO, false, false);
        assertFalse(chosen.contains("palette-"), "선택 영역 수정에 팔레트가 안내됐습니다");
        assertFalse(chosen.contains("페이지 전체 색감"));
        assertTrue(chosen.contains("v-hero-left"), "hero 자체 모양 변형은 그대로 안내해야 합니다");

        assertTrue(PromptBuilder.edit(Block.HERO, false, true).contains("palette-summer"), "영역을 안 고르면 지금처럼 안내");
        // 템플릿 hero + 선택 영역이면 고를 것이 없다 — 모양 고르기 절 자체가 없어야 한다
        assertFalse(PromptBuilder.edit(Block.HERO, true, false).contains("모양 고르기"));
    }

    @Test
    @DisplayName("라우터는 페이지 색감 요청을 hero STYLE 로 보내는 예시를 갖는다")
    void 라우터_예시() {
        assertTrue(PromptBuilder.router().contains("\"op\":\"STYLE\",\"target\":\"hero\""));
    }
}
