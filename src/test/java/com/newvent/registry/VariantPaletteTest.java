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
            if (!v.core()) assertTrue(v.desc().contains(" — "), v + " 설명에 \"이름 — 설명\" 꼴이 없습니다 (열쇠말을 못 뽑는다)");
        }
        Set<String> palettes = new HashSet<>();
        for (Palette p : Palette.values()) {
            assertTrue(p.cssClass().startsWith(Palette.PREFIX), p + " 가 palette- 로 시작하지 않습니다");
            assertTrue(palettes.add(p.cssClass()), p.cssClass() + " 가 두 번 나옵니다");
            if (!p.core()) assertTrue(p.desc().contains(" — "), p + " 설명에 \"이름 — 색\" 꼴이 없습니다 (열쇠말을 못 뽑는다)");
        }
    }

    @Test
    @DisplayName("팔레트 확장: 기본 19개는 그대로, 확장은 125개, 어두운 팔레트는 night · esports 포함 24개")
    void 팔레트_확장_개수() {
        assertEquals(19, Palette.basics().size());
        assertEquals(19 + 125, Palette.values().length);
        assertEquals(24, Palette.all().stream().filter(Palette::dark).count());
        // 기본은 선언 맨 앞에 몰려 있다 — 프롬프트의 기본 목록 순서가 확장 때문에 흔들리지 않는다
        for (int i = 0; i < 19; i++) assertTrue(Palette.values()[i].core(), Palette.values()[i] + " 는 기본이어야 합니다");
    }

    @Test
    @DisplayName("팔레트 확장: 요청문에 이름이 나오면 기본 뒤에 붙고, 없으면 기본만")
    void 팔레트_확장_요청문() {
        assertEquals(Palette.basics(), Palette.forPrompt(null));
        assertEquals(Palette.basics(), Palette.forPrompt("신규 가입 이벤트 페이지를 만들어줘"));

        List<Palette> jeju = Palette.forPrompt("제주 감성으로 여름 여행 이벤트");
        assertTrue(jeju.contains(Palette.JEJU));
        assertEquals(Palette.basics(), jeju.subList(0, 19), "기본 목록이 앞에 그대로 있어야 합니다");

        assertEquals(Palette.CHUSEOK, Palette.matched("추석 맞이 한가위 선물").get(0));
        assertTrue(Palette.matched("단청 느낌으로 전통스럽게").contains(Palette.DANCHEONG));
    }

    @Test
    @DisplayName("팔레트 확장: 흔한 색 이름 · '기본' 같은 말은 확장을 끌어오지 않는다")
    void 팔레트_확장_흔한말() {
        assertEquals(List.of(), Palette.matched("전체 색을 보라색으로 바꿔줘"));
        assertEquals(List.of(), Palette.matched("기본 정보에 맞게 만들어줘"));
        // 많이 맞아도 MAX_MATCHED 를 넘지 않는다
        assertTrue(Palette.matched("단청 오방색 한복 청자 백자 제주 부산 한옥 서울 야경").size() <= Palette.MAX_MATCHED);
    }

    /**
     * ★ 기본 122개는 그대로다 — 확장이 들어와도 표본 · 수정 목록의 기본 부분은 한 글자도 안 바뀐다.
     *   확장(배치 56 · 배경 16 · 움직임 8, registry-candidates.json 의 채택분)은 요청문이 이름을 부를 때만 붙는다.
     */
    @Test
    @DisplayName("변형 확장: 기본 122개 + 확장 80개(배치 56 · 배경 16 · 움직임 8), 요청문이 안 부르면 예전 목록 그대로")
    void 변형_확장_구조() {
        List<Variant> all = List.of(Variant.values());
        assertEquals(122, all.stream().filter(Variant::core).count());
        assertEquals(80, all.stream().filter(v -> !v.core()).count());
        assertEquals(56, all.stream().filter(v -> !v.core() && v.group() == Variant.Group.LAYOUT).count());
        // ★ 배경 확장은 무늬 · 질감 · 경계뿐이다 — 고정 색 배경(namedColor)은 늘리지 않는다
        assertEquals(16, all.stream().filter(v -> !v.core() && v.group() == Variant.Group.SURFACE).count());
        assertTrue(all.stream().filter(v -> !v.core() && v.group() == Variant.Group.SURFACE)
                .noneMatch(Variant::namedColor));
        // ★ 움직임은 전부 확장이다 — 아무 말이 없는데 페이지가 움직이면 안 된다
        assertTrue(all.stream().filter(v -> v.group() == Variant.Group.MOTION).noneMatch(Variant::core));
        assertEquals(8, all.stream().filter(v -> v.group() == Variant.Group.MOTION).count());
        // 배치 확장은 블록 전용이다
        assertTrue(all.stream().filter(v -> !v.core() && v.group() == Variant.Group.LAYOUT)
                .allMatch(v -> v.block() != null && !v.namedColor()));
        for (Block b : Block.llmBlocks()) {
            List<Variant> core = Variant.of(b).stream().filter(Variant::core).toList();
            assertEquals(core, Variant.forEdit(b, "신규 가입 이벤트 혜택을 다듬어줘"), b.key());
            assertEquals(Variant.sample(b, "가을 이벤트", 4), Variant.forPrompt(b, "가을 이벤트", 4), b.key());
            assertTrue(Variant.sample(b, "영수증 메달 항공권", 4).stream().allMatch(Variant::core),
                    b.key() + " 표본에 확장이 섞였습니다");
        }
        // ★ "가을" 은 이제 낙엽 무늬(배경 확장)를 부른다 — 확장을 부르지 않는 말로 본다
        assertEquals(Variant.sampleSurfaces("신규 가입", 4), Variant.surfacesForPrompt("신규 가입", 4));
        assertFalse(PromptBuilder.generate("신규 가입 이벤트").contains("요청문이 말한 모양과"),
                "요청문이 안 불렀는데 확장 안내 줄이 나갔습니다");
    }

    @Test
    @DisplayName("변형 확장: 이름을 부르면 그 블록 목록 뒤에 붙고, 다른 블록에는 안 붙는다")
    void 변형_확장_요청문() {
        assertEquals(List.of(Variant.BENEFITS_RECEIPT), Variant.matched(Block.BENEFITS, "혜택을 영수증 모양으로"));
        assertEquals(List.of(), Variant.matched(Block.STEPS, "혜택을 영수증 모양으로"));
        assertEquals(List.of(Variant.PRIZE_MEDAL), Variant.matched(Block.PRIZE, "경품을 메달처럼 보여줘"));
        assertEquals(List.of(Variant.COUPON_BOARDING_PASS), Variant.matched(Block.COUPON, "탑승권 느낌 쿠폰"));
        assertEquals(List.of(Variant.COMPARE_VS), Variant.matched(Block.COMPARE, "A vs B 대결 구도로"));
        // 같은 이름(브루탈)이 두 블록에 있으면 블록마다 자기 것만
        assertEquals(List.of(Variant.HERO_BRUTAL), Variant.matched(Block.HERO, "브루탈하게"));
        assertEquals(List.of(Variant.CTA_BRUTAL), Variant.matched(Block.CTA, "브루탈하게"));

        String gen = PromptBuilder.generate("영수증 모양 혜택으로 신규 가입 이벤트");
        assertTrue(gen.contains("v-benefits-receipt"));
        assertTrue(gen.contains("요청문이 말한 모양과 이름이 맞는 것: v-benefits-receipt"));
        String edit = PromptBuilder.edit(Block.BENEFITS, false, false, true, false, List.of(), "영수증 모양으로 바꿔줘");
        assertTrue(edit.contains("v-benefits-receipt") && edit.contains("요청문이 말한 모양과"));
    }

    /**
     * ★ Bedrock 실측 두 건을 고정한다 (이벤트 5682 — "봄 벚꽃 축제")
     *   ① "페이지는 벽돌 배치로" 가 혜택 변형 v-benefits-masonry(벽돌)까지 끌어와 "선물 포장" 대신 골라졌다
     *   ② "종이 공예처럼 아기자기하게" 인데 요청문의 "축제" 때문에 theme-bloom 이 골라졌다
     */
    @Test
    @DisplayName("변형 · 테마 짚기: 페이지 배치 얘기는 변형을 끌어오지 않고, 이름이 더 맞는 테마를 짚는다")
    void 배치_얘기와_테마_짚기() {
        String text = "봄 벚꽃 축제 이벤트. 종이 공예처럼 아기자기하고 따뜻하게. 페이지는 벽돌 배치로. "
                + "혜택은 선물 포장 모양으로.";
        assertFalse(Variant.matched(Block.BENEFITS, text).contains(Variant.BENEFITS_MASONRY),
                "페이지 배치(벽돌) 얘기가 혜택 변형을 끌어왔습니다");
        assertTrue(Variant.matched(Block.BENEFITS, text).contains(Variant.BENEFITS_GIFT));
        // 영역 모양으로 벽돌을 말하면 그때는 실린다
        assertTrue(Variant.matched(Block.BENEFITS, "혜택 카드를 벽돌처럼 쌓아줘").contains(Variant.BENEFITS_MASONRY));

        assertEquals(java.util.Optional.of(Theme.PAPERCRAFT), Theme.matched(text));
        assertEquals(java.util.Optional.of(Theme.BRUTALIST), Theme.matched("브루탈하고 힙하게"));
        assertEquals(java.util.Optional.empty(), Theme.matched("신규 가입 이벤트"));
        assertTrue(PromptBuilder.generate(text).contains("이름이 맞는 테마: theme-papercraft"));
        assertTrue(PromptBuilder.edit(Block.HERO, false, true, true, false, List.of(), "종이 공예 느낌으로 바꿔줘")
                .contains("이름이 맞는 테마: theme-papercraft"));
    }

    @Test
    @DisplayName("배경 확장: 계절 · 질감 말이 나오면 생성 배경 목록에 붙고, 배경을 쓰는 블록의 수정 목록에도 붙는다")
    void 배경_확장() {
        assertTrue(Variant.matchedSurfaces("겨울 눈송이 이벤트").contains(Variant.SURFACE_SNOW));
        assertTrue(Variant.matchedSurfaces("가을 단풍 축제").contains(Variant.SURFACE_LEAVES));
        assertEquals(List.of(), Variant.matchedSurfaces("신규 가입 이벤트"));
        String gen = PromptBuilder.generate("한지 느낌의 전통 명절 이벤트");
        assertTrue(gen.contains("v-surface-hanji"), "배경 확장이 생성 프롬프트에 안 실렸습니다");
        // 배경을 쓰는 블록(intro)에는 실리고, 안 쓰는 블록(cta)에는 안 실린다
        assertTrue(Variant.forEdit(Block.INTRO, "공책 줄노트처럼").contains(Variant.SURFACE_NOTEBOOK));
        assertFalse(Variant.forEdit(Block.CTA, "공책 줄노트처럼").contains(Variant.SURFACE_NOTEBOOK));
    }

    @Test
    @DisplayName("움직임: hero 는 흩날림만, cta 는 버튼 강조 + 공통 등장, 유의사항에는 없다, 배치와 같이 남는다")
    void 움직임() {
        assertTrue(Variant.of(Block.HERO).contains(Variant.HERO_ANIM_SNOWFALL));
        assertFalse(Variant.of(Block.HERO).contains(Variant.ANIM_FADE_UP), "hero 는 첫 화면이라 등장 효과가 없습니다");
        assertTrue(Variant.of(Block.CTA).containsAll(List.of(Variant.CTA_ANIM_PULSE, Variant.ANIM_FADE_UP)));
        assertTrue(Variant.of(Block.BENEFITS).contains(Variant.ANIM_STAGGER));
        assertFalse(Variant.of(Block.BENEFITS).contains(Variant.HERO_ANIM_SNOWFALL));
        assertTrue(Variant.of(Block.NOTICES).stream().noneMatch(v -> v.group() == Variant.Group.MOTION));

        assertEquals(List.of(Variant.HERO_ANIM_SNOWFALL), Variant.matched(Block.HERO, "함박눈이 내리는 겨울 이벤트"));
        assertEquals(List.of(Variant.ANIM_STAGGER), Variant.matchedMotions("혜택이 차례로 나타나게"));
        String gen = PromptBuilder.generate("벚꽃 꽃잎이 흩날리는 봄 이벤트");
        assertTrue(gen.contains("v-anim-petal-fall") && gen.contains("움직임:"), "hero 움직임이 생성 프롬프트에 안 실렸습니다");
        assertFalse(PromptBuilder.generate("신규 가입 이벤트").contains("v-anim-"), "요청문 없이 움직임이 실렸습니다");

        // 묶음이 다르다 — 배치 하나 + 움직임 하나가 같이 남고, 움직임 둘이면 앞의 것만
        Element sec = root("<section data-block=\"cta\" class=\"v-cta-pill v-anim-pulse v-anim-heart-beat\"><a class=\"btn\">x</a></section>",
                "section");
        Variant.sanitize(sec, Block.CTA);
        assertEquals(Set.of("v-cta-pill", "v-anim-pulse"), sec.classNames());
    }

    /**
     * ★ 흔한 말이 확장을 끌어오면 블록마다 엉뚱한 모양이 실린다.
     *   이벤트 요청에 자주 나오는 문장이 확장을 하나도 안 부르는지 본다.
     */
    @Test
    @DisplayName("변형 확장: 흔한 요청 문장은 확장을 끌어오지 않는다")
    void 변형_확장_흔한말() {
        List<String> plain = List.of(
                "신규 가입 고객에게 데이터 3GB 를 주는 이벤트",
                "혜택 카드를 크게 보여줘",
                "참여 방법을 단계별로 정리해줘",
                "경품 목록을 큰 글씨로",
                "버튼을 더 눈에 띄게",
                "자주 묻는 질문을 깔끔하게",
                "일정을 보기 좋게 바꿔줘",
                "요금제 비교표를 만들어줘",
                "숫자를 크게 강조해줘",
                "전체 배경을 밝게");
        for (String text : plain) {
            for (Block b : Block.llmBlocks()) {
                assertEquals(List.of(), Variant.matched(b, text), b.key() + " ← \"" + text + "\"");
            }
        }
    }

    @Test
    @DisplayName("팔레트 확장: 생성 · 수정 프롬프트에 요청문이 부른 확장이 실린다")
    void 팔레트_확장_프롬프트() {
        assertTrue(PromptBuilder.generate("제주 감성 여름 이벤트").contains("palette-jeju"));
        assertFalse(PromptBuilder.generate("신규 가입 이벤트").contains("palette-jeju"));
        assertTrue(PromptBuilder.edit(Block.HERO, false, true, true, false, List.of(), "전체를 단청 색으로 바꿔줘")
                .contains("palette-dancheong"));
        assertFalse(PromptBuilder.edit(Block.HERO, false, true, true, false, List.of())
                .contains("palette-dancheong"));
        // 목록에만 두면 모델이 못 고른다 (Bedrock 실측) — 목록 아래에서 한 번 더 짚는다
        String edit = PromptBuilder.edit(Block.HERO, false, true, true, false, List.of(), "전체 색을 단청 느낌으로 바꿔줘");
        assertTrue(edit.contains("이름이 맞는 색감: palette-dancheong"), "요청문이 부른 팔레트를 짚는 줄이 없습니다");
        assertFalse(PromptBuilder.edit(Block.HERO, false, true, true, false, List.of(), "전체 색을 보라색으로")
                .contains("이름이 맞는 색감"), "흔한 색 요청에는 짚는 줄이 없어야 합니다");
        assertTrue(PromptBuilder.generate("제주 감성 여름 이벤트").contains("이름이 맞는 색감: palette-jeju"));
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
    @DisplayName("벤토는 항목이 3개 이상일 때만 남는다 — 2개면 class 만 떼고 내용 · 배경은 둔다")
    void 벤토는_항목이_모자라면_뗀다() {
        String[][] cases = {
                {"benefits", "v-benefits-bento", "ul", "li"},
                {"stats", "v-stats-bento", "ul", "li"},
                {"prize", "v-prize-bento", "ul", "li"},
        };
        for (String[] c : cases) {
            String open = "<section data-block=\"" + c[0] + "\" class=\"" + c[1] + " v-surface-tint\"><h2>t</h2><" + c[2] + ">";
            String close = "</" + c[2] + "></section>";
            String item = "<" + c[3] + "><strong>1</strong> a</" + c[3] + ">";

            Element three = root(BlockValidator.sanitizeGenerated(open + item.repeat(3) + close), "section");
            assertTrue(three.hasClass(c[1]), c[1] + " 는 항목 3개면 남아야 합니다");

            Element two = root(BlockValidator.sanitizeGenerated(open + item.repeat(2) + close), "section");
            assertFalse(two.hasClass(c[1]), c[1] + " 는 항목 2개면 떼야 합니다 — 큰 칸 옆이 빕니다");
            assertEquals(2, two.select(c[3]).size(), "class 만 떼고 항목은 그대로 둡니다");
            assertTrue(two.hasClass("v-surface-tint"), "배경 변형은 건드리지 않습니다");
        }
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

    @Test
    @DisplayName("프롬프트 — 생성에는 배치를 고르는 기준이 있다")
    void 프롬프트의_기준() {
        String gen = PromptBuilder.generate("가을 이벤트");
        assertTrue(gen.contains("고르는 기준"), "배치를 고르는 기준이 생성 프롬프트에 없습니다");
    }

    // ── 페이지 배치 ──

    private static String page(String rootClass, String heroClass, int contentBlocks) {
        String[] keys = {"intro", "benefits", "steps", "faq", "audience"};
        StringBuilder sb = new StringBuilder("<div class=\"ev-container event-page " + rootClass + "\">"
                + "<section data-block=\"hero\"" + (heroClass.isEmpty() ? "" : " class=\"" + heroClass + "\"") + "><h1>t</h1></section>");
        for (int i = 0; i < contentBlocks; i++) sb.append("<section data-block=\"").append(keys[i]).append("\"><p>x</p></section>");
        return sb.append("<section data-block=\"cta\"><a href=\"#\" class=\"btn\">go</a></section></div>").toString();
    }

    @Test
    @DisplayName("배치 class 이름이 겹치지 않고 전부 layout- 로 시작한다")
    void 배치_이름() {
        Set<String> seen = new HashSet<>();
        for (Layout l : Layout.values()) {
            assertTrue(l.cssClass().startsWith(Layout.PREFIX), l + " 가 layout- 로 시작하지 않습니다");
            assertTrue(seen.add(l.cssClass()), l.cssClass() + " 가 두 번 나옵니다");
        }
        assertEquals(0, Layout.DEFAULT.minContentBlocks(), "기본 배치는 조건이 없어야 합니다");
    }

    @Test
    @DisplayName("정화는 배치를 hero 에만, 하나만, 아는 것만 남긴다 — 루트의 것은 건드리지 않는다")
    void 정화가_배치를_거른다() {
        String dirty = "<div class=\"ev-container event-page layout-wide\">"
                + "<section data-block=\"hero\" class=\"layout-bento layout-split layout-made-up\"><h1>t</h1></section>"
                + "<section data-block=\"benefits\" class=\"layout-bento\"><ul><li>a</li><li>b</li></ul></section></div>";

        String clean = BlockValidator.sanitizeEdited(dirty);

        Element hero = root(clean, "[data-block=hero]");
        assertTrue(hero.hasClass("layout-bento"), "먼저 쓴 배치가 남아야 합니다: " + clean);
        assertFalse(hero.hasClass("layout-split"), "두 번째는 지워야 합니다");
        assertFalse(hero.hasClass("layout-made-up"), "목록에 없는 배치는 지워야 합니다");
        assertFalse(root(clean, "[data-block=benefits]").hasClass("layout-bento"), "hero 밖의 배치는 지워야 합니다");
        assertTrue(root(clean, ".ev-container").hasClass("layout-wide"), "루트의 배치는 서버가 쥡니다 — 정화가 지우면 안 됩니다");
    }

    @Test
    @DisplayName("★ 저장할 때 hero 의 배치가 루트로 옮겨지고, stack 이면 지운다 — 안 고른 수정은 그대로 둔다")
    void 배치를_루트로_옮긴다() {
        Element r1 = root(PageShell.settle(page("theme-basic", "v-hero-left layout-bento", 4)), ".ev-container");
        assertTrue(r1.hasClass("layout-bento"), "루트에 배치가 없습니다: " + r1.className());
        assertFalse(r1.selectFirst("[data-block=hero]").hasClass("layout-bento"), "hero 에 배치가 남았습니다");
        assertTrue(r1.selectFirst("[data-block=hero]").hasClass("v-hero-left"), "변형은 hero 에 남아야 합니다");

        Element r2 = root(PageShell.settle(page("theme-basic layout-bento", "layout-wide", 4)), ".ev-container");
        assertTrue(r2.hasClass("layout-wide") && !r2.hasClass("layout-bento"), "새 배치로 바뀌어야 합니다: " + r2.className());

        Element r3 = root(PageShell.settle(page("theme-basic layout-bento", "layout-stack", 4)), ".ev-container");
        assertTrue(r3.classNames().stream().noneMatch(Layout::looksLike), "stack 이면 배치 class 가 없어야 합니다: " + r3.className());

        Element r4 = root(PageShell.settle(page("theme-basic layout-bento", "", 4)), ".ev-container");
        assertTrue(r4.hasClass("layout-bento"), "배치를 안 고른 수정은 루트를 건드리지 않습니다");
        assertTrue(r4.hasClass("theme-basic"), "테마는 그대로 둬야 합니다");
    }

    @Test
    @DisplayName("★ 배치는 내용 영역이 모자라면 기본으로 되돌린다 — 고를 때도, 나중에 영역이 줄어도")
    void 배치_조건() {
        Element few = root(PageShell.settle(page("theme-basic", "layout-bento", 3)), ".ev-container");
        assertFalse(few.hasClass("layout-bento"), "내용 영역 3개에 벤토가 남았습니다 — 격자가 빕니다");

        Element shrunk = root(PageShell.settle(page("theme-basic layout-bento", "", 3)), ".ev-container");
        assertFalse(shrunk.hasClass("layout-bento"), "영역이 줄었는데 벤토가 남았습니다");

        Element split = root(PageShell.settle(page("theme-basic", "layout-split", 2)), ".ev-container");
        assertTrue(split.hasClass("layout-split"));
        Element splitFew = root(PageShell.settle(page("theme-basic", "layout-split", 1)), ".ev-container");
        assertFalse(splitFew.hasClass("layout-split"));
    }

    @Test
    @DisplayName("★ 템플릿 페이지에는 배치를 걸지 않는다 — 템플릿 블록은 자기 디자인이 있다")
    void 템플릿에는_배치_없음() {
        String tpl = page("theme-sports", "layout-bento", 4).replace("data-block=\"benefits\"", "data-block=\"benefits\" class=\"ev-block sp-benefits\"");
        Element r = root(PageShell.settle(tpl), ".ev-container");
        assertTrue(r.classNames().stream().noneMatch(Layout::looksLike), "템플릿 페이지에 배치가 걸렸습니다: " + r.className());
        assertTrue(r.hasClass("theme-sports"));
    }

    @Test
    @DisplayName("★ 서버가 넣은 유의사항(.ev-block)이 있어도 백지 페이지는 템플릿이 아니다 — 배치가 남아야 한다")
    void 유의사항은_템플릿_판정에서_뺀다() {
        String planted = PageShell.plant("<section data-block=\"hero\" class=\"layout-bento\"><h1>t</h1></section>"
                + "<section data-block=\"intro\"><p>x</p></section><section data-block=\"benefits\"><ul><li>a</li><li>b</li></ul></section>"
                + "<section data-block=\"steps\"><ol><li>a</li><li>b</li></ol></section><section data-block=\"faq\"><dl><dt>q</dt><dd>a</dd><dt>q</dt><dd>a</dd></dl></section>"
                + "<section data-block=\"cta\"><a href=\"#\" class=\"btn\">go</a></section>", null);
        assertNotNull(root(planted, "[data-block=notices]"), "유의사항이 들어가야 합니다");
        // 유의사항이 들어간 뒤의 수정 저장 — 여기서 떨어졌다
        Element r = root(PageShell.settle(planted.replace("data-block=\"hero\"", "data-block=\"hero\" class=\"layout-bento\"")), ".ev-container");
        assertTrue(r.hasClass("layout-bento"), "유의사항 때문에 템플릿으로 판정됐습니다: " + r.className());
    }

    @Test
    @DisplayName("★ 3열 · 매거진 · 지그재그 — 열쇠말과 조건. 영역 변형(v-benefits-three-col · v-steps-zigzag)과 헷갈리지 않는다")
    void 추가_배치_셋() {
        Object[][] hits = {
                {"영역들을 3열로 배치해줘", Layout.THREE},
                {"페이지를 세 열로 보여줘", Layout.THREE},
                {"3단 레이아웃으로 바꿔줘", Layout.THREE},
                {"매거진 레이아웃으로 바꿔줘", Layout.MAGAZINE},
                {"잡지 스타일의 봄 이벤트 페이지", Layout.MAGAZINE},
                {"페이지를 매거진처럼 바꿔줘", Layout.MAGAZINE},
                {"잡지처럼 큼직하고 시원하게 읽히는 페이지로", Layout.MAGAZINE},
                {"지그재그 배치로 바꿔줘", Layout.ZIGZAG},
                {"영역들을 좌우로 번갈아 놓아줘", Layout.ZIGZAG},
                {"페이지를 지그재그로 바꿔줘", Layout.ZIGZAG},
                // 2열과 3열은 숫자로만 갈린다
                {"영역들을 2열로 넓게 배치해줘", Layout.WIDE},
        };
        for (Object[] h : hits) {
            assertEquals(java.util.Optional.of(h[1]), Layout.requestedBy((String) h[0]), (String) h[0]);
        }
        for (String miss : List.of(
                "혜택을 3열 카드로 보여줘", "혜택을 3열로 배치해줘", "참여 방법을 3단계로 줄여줘",
                "참여 방법을 지그재그로 바꿔줘", "참여 방법을 지그재그 배치로 바꿔줘",
                "혜택 카드를 매거진 스타일로 바꿔줘", "지그재그로 바꿔줘", "잡지에 실린 이벤트")) {
            assertTrue(Layout.requestedBy(miss).isEmpty(), "페이지 배치로 읽혔습니다: " + miss + " → " + Layout.requestedBy(miss));
        }

        // 조건 — 3열 · 매거진은 내용 영역 셋(대표 하나 + 나란히 둘), 지그재그는 둘
        assertTrue(PageShell.layoutBlocked(page("theme-basic", "", 2), Layout.THREE).contains("3개 이상"));
        assertNull(PageShell.layoutBlocked(page("theme-basic", "", 3), Layout.THREE));
        assertTrue(PageShell.layoutBlocked(page("theme-basic", "", 2), Layout.MAGAZINE).contains("3개 이상"));
        assertNull(PageShell.layoutBlocked(page("theme-basic", "", 3), Layout.MAGAZINE));
        assertTrue(PageShell.layoutBlocked(page("theme-basic", "", 1), Layout.ZIGZAG).contains("2개 이상"));
        assertNull(PageShell.layoutBlocked(page("theme-basic", "", 2), Layout.ZIGZAG));

        // 저장 — 루트로 옮겨지고, 영역이 모자라면 떨어진다
        for (Layout l : List.of(Layout.THREE, Layout.MAGAZINE, Layout.ZIGZAG)) {
            assertTrue(root(PageShell.settle(page("theme-basic", l.cssClass(), 3)), ".ev-container").hasClass(l.cssClass()), l.cssClass());
            assertFalse(root(PageShell.settle(page("theme-basic", l.cssClass(), 1)), ".ev-container").hasClass(l.cssClass()),
                    l.cssClass() + " 가 영역 하나인 페이지에 남았습니다");
        }
        // 배치만 말한 요청이다 — 모델을 부르지 않는다
        for (String only : List.of("영역들을 3열로 배치해줘", "매거진 레이아웃으로 바꿔줘", "페이지를 지그재그로 바꿔줘")) {
            assertFalse(Layout.asksMoreOfHero(only), only);
        }
    }

    @Test
    @DisplayName("★ 풀블리드 · 포커스 — 열쇠말. 조건이 없어 영역이 하나여도 걸린다. hero 모양 요청과 헷갈리지 않는다")
    void 풀블리드와_포커스() {
        Object[][] hits = {
                {"풀블리드 레이아웃으로 바꿔줘", Layout.FULL},
                {"랜딩 페이지처럼 만들어줘", Layout.FULL},
                {"영역들을 화면 폭 끝까지 쓰는 띠 배치로", Layout.FULL},
                {"페이지를 화면에 꽉 차게 바꿔줘", Layout.FULL},
                {"포커스 레이아웃으로 바꿔줘", Layout.FOCUS},
                {"페이지 폭을 좁게 해줘", Layout.FOCUS},
                {"좁은 한 줄 배치로 바꿔줘", Layout.FOCUS},
        };
        for (Object[] h : hits) {
            assertEquals(java.util.Optional.of(h[1]), Layout.requestedBy((String) h[0]), (String) h[0]);
        }
        for (String miss : List.of(
                "제목을 화면 가득 채워줘", "버튼을 화면 폭에 꽉 차게 해줘", "혜택 카드 폭을 좁게 해줘",
                "제목에 포커스를 줘", "혜택을 띠 배너로 강조해줘", "꽉 찬 느낌으로 바꿔줘")) {
            assertTrue(Layout.requestedBy(miss).isEmpty(), "페이지 배치로 읽혔습니다: " + miss + " → " + Layout.requestedBy(miss));
        }
        for (Layout l : List.of(Layout.FULL, Layout.FOCUS)) {
            assertEquals(0, l.minContentBlocks(), l + " 는 조건이 없다");
            assertNull(PageShell.layoutBlocked(page("theme-basic", "", 1), l));
            assertTrue(root(PageShell.settle(page("theme-basic", l.cssClass(), 1)), ".ev-container").hasClass(l.cssClass()), l.cssClass());
        }
        // 템플릿 페이지에는 조건이 없는 배치도 걸지 않는다
        String tpl = page("theme-sports", "", 2).replace("data-block=\"intro\"", "data-block=\"intro\" class=\"ev-block\"");
        assertTrue(PageShell.layoutBlocked(tpl, Layout.FULL).contains("템플릿"));
    }

    @Test
    @DisplayName("★ 걸 수 없는 배치는 모델을 부르기 전에 이유를 말한다 — 영역 부족 · 템플릿 · 이미 그 배치")
    void 배치_사전_검사() {
        assertNull(PageShell.layoutBlocked(page("theme-basic", "", 4), Layout.BENTO));
        assertTrue(PageShell.layoutBlocked(page("theme-basic", "", 3), Layout.BENTO).contains("4개 이상"));
        assertTrue(PageShell.layoutBlocked(page("theme-basic layout-bento", "", 4), Layout.BENTO).contains("이미"));
        assertTrue(PageShell.layoutBlocked(page("theme-basic", "", 4), Layout.STACK).contains("이미"), "배치가 없으면 이미 기본이다");
        assertNull(PageShell.layoutBlocked(page("theme-basic layout-bento", "", 4), Layout.STACK));

        String tpl = page("theme-sports", "", 4).replace("data-block=\"benefits\"", "data-block=\"benefits\" class=\"ev-block\"");
        assertTrue(PageShell.layoutBlocked(tpl, Layout.WIDE).contains("템플릿"));
    }

    @Test
    @DisplayName("★ 서버가 배치를 고르면 hero 의 모양 · 문구는 그대로다 — 배치 class 하나만 바뀐다")
    void 서버가_고른_배치() {
        String doc = page("theme-basic palette-winter", "v-hero-center", 4);
        Element r = root(PageShell.settle(PageShell.pickLayout(doc, Layout.SPLIT)), ".ev-container");
        assertTrue(r.hasClass("layout-split") && r.hasClass("palette-winter") && r.hasClass("theme-basic"), r.className());
        Element hero = r.selectFirst("[data-block=hero]");
        assertEquals("v-hero-center", hero.className(), "hero 모양이 바뀌었습니다");
        assertEquals("t", hero.text());

        Element back = root(PageShell.settle(PageShell.pickLayout(r.outerHtml(), Layout.STACK)), ".ev-container");
        assertTrue(back.classNames().stream().noneMatch(Layout::looksLike), back.className());
    }

    @Test
    @DisplayName("배치를 고른 적 없는 페이지는 저장해도 글자 하나 달라지지 않는다")
    void 배치_없는_페이지는_그대로() {
        String doc = page("theme-basic palette-night", "", 2);
        assertEquals(doc, PageShell.settle(doc));
    }

    @Test
    @DisplayName("★ 배치 안내 — 생성과 '영역을 안 고른 백지 hero 수정' 에만 나가고, 라우터는 hero STYLE 예시를 갖는다")
    void 배치_프롬프트() {
        String gen = PromptBuilder.generate("가을 이벤트");
        for (Layout l : Layout.all()) assertTrue(gen.contains(l.cssClass()), l.cssClass() + " 가 생성 프롬프트에 없습니다");

        String hero = PromptBuilder.edit(Block.HERO, false, true);
        for (Layout l : Layout.all()) assertTrue(hero.contains(l.cssClass()), l.cssClass() + " 가 hero 수정 프롬프트에 없습니다");

        assertFalse(PromptBuilder.edit(Block.HERO, false, false).contains(Layout.PREFIX), "영역을 고른 수정에 배치가 안내됐습니다 — 선택 밖이 움직입니다");
        assertFalse(PromptBuilder.edit(Block.HERO, true, true).contains(Layout.PREFIX), "템플릿 hero 에 배치가 안내됐습니다");
        assertFalse(PromptBuilder.edit(Block.BENEFITS, false, true).contains(Layout.PREFIX), "hero 가 아닌 영역에 배치가 안내됐습니다");

        assertTrue(PromptBuilder.router().contains("벤토 그리드 배치"));
    }

    @Test
    @DisplayName("수정에서 배치를 붙이는 건 class_changed 가 아니다")
    void 배치는_class_changed_아님() {
        String before = "<section data-block=\"hero\" class=\"v-hero-left\"><h1>t</h1></section>";
        String after = "<section data-block=\"hero\" class=\"v-hero-left layout-bento\"><h1>t</h1></section>";
        assertTrue(BlockValidator.validateEdited(Block.HERO, before, after).isEmpty(),
                "배치를 붙였더니 검증에 걸렸습니다: " + BlockValidator.validateEdited(Block.HERO, before, after));
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
        // ★ 기본 팔레트만 늘 실린다. 확장은 요청문이 부를 때만이다 (아래 팔레트_확장_* 테스트)
        for (Palette pl : Palette.basics()) {
            if (pl != Palette.BASE) assertTrue(p.contains(pl.cssClass()), pl.cssClass());
        }
        assertFalse(p.contains("palette-jeju"), "요청문 없이 확장 팔레트가 실렸습니다");
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
            // ★ 확장은 요청문이 부를 때만 나간다 — 이름을 말하면 나가는지 본다
            if (!v.core()) {
                String name = v.desc().substring(0, v.desc().indexOf(" — "));
                boolean called = Block.llmBlocks().stream()
                        .filter(b -> Variant.of(b).contains(v))
                        .anyMatch(b -> PromptBuilder.edit(b, false, false, true, false, List.of(), name + " 모양으로")
                                .contains(v.cssClass()));
                assertTrue(called, v.cssClass() + " 는 이름(" + name + ")을 불러도 수정 프롬프트에 안 나갑니다");
                continue;
            }
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
            // ★ 표본은 기본에서만 뽑는다. 확장은 요청문이 부를 때만 붙는다 (변형_확장_*)
            List<Variant> layouts = Variant.of(b).stream()
                    .filter(v -> v.group() == Variant.Group.LAYOUT && v.core()).toList();
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

    /**
     * ★★ "제목·소개 참여 방법 더 화려하게" 에 모델이 이모지와 <strong> 만 붙이고
     *   레이아웃은 오히려 v-hero-split → 기본 가운데 정렬로 밋밋해졌다(실제 화면).
     *   원인이 네 군데였고 이 테스트가 그 네 개를 하나씩 못 박는다.
     */
    @Test
    @DisplayName("★ 겉모양 요청(STYLE)은 모양 고르기를 **단정문**으로 받는다 — 조건문이면 모델이 문구로 샌다")
    void 겉모양_요청은_단정문() {
        String style = PromptBuilder.edit(Block.HERO, false, true, true);
        String plain = PromptBuilder.edit(Block.HERO, false, true, false);

        // ① 라우터의 op 이 실제로 프롬프트를 바꾼다. 전에는 두 글이 **완전히 같았다**
        assertNotEquals(plain, style, "STYLE 과 EDIT 의 프롬프트가 같으면 라우터가 분류한 의미가 없습니다");

        // ② 조건문이 사라지고 단정문이 온다
        assertFalse(style.contains("바꿔 달라는 요청일 때만"),
                "STYLE 인데 '…일 때만' 이 남아 있으면 모델이 또 스스로 판단하다 문구로 샙니다");
        assertTrue(style.contains("반드시 하나를 골라 바꾼다"), style);
        assertTrue(style.contains("문구만 고치고 끝내지 마라"));
        assertTrue(style.contains("다른** 이름을 고른다"), "같은 변형을 다시 고르면 화면이 안 바뀝니다");

        // ③ 앞과 뒤에 두 번 — 작은 모델은 가운데를 흘린다
        assertTrue(style.indexOf("겉모습") < style.indexOf("모양 고르기"), "앞쪽에도 한 번 나와야 합니다");

        // ④ EDIT 쪽은 건드리지 않았다 — 문구 수정이 모양까지 바꾸면 그게 또 사고다
        assertTrue(plain.contains("바꿔 달라는 요청일 때만"));
        assertFalse(plain.contains("반드시 하나를 골라 바꾼다"));
    }

    @Test
    @DisplayName("★ 수정에서도 테마를 고를 수 있다 — 백지 테마 3종이 수정 프롬프트에 없었다")
    void 수정에서_테마를_고른다() {
        String hero = PromptBuilder.edit(Block.HERO, false, true, true);
        for (Theme t : Theme.blankThemes()) {
            assertTrue(hero.contains(t.cssClass()),
                    t.cssClass() + " 가 수정 프롬프트에 없습니다. 모델이 영영 못 고릅니다");
        }
        // 구조를 먼저 정하고 색을 얹는다 — 생성(addLooks)과 같은 순서
        assertTrue(hero.indexOf("페이지 전체 분위기") < hero.indexOf("페이지 전체 색감"));

        // hero 가 아니면 테마는 안 나온다 — 페이지 전체 값이라 자리가 하나여야 한다
        assertFalse(PromptBuilder.edit(Block.STEPS, false, true, true).contains("theme-bloom"));

        // ★ 템플릿에는 안 나온다. 템플릿 루트에는 theme-sports 처럼 다른 벌이 붙어 있어서
        //   blankThemes 를 고르면 hoist 가 그걸 갈아끼워 템플릿 디자인이 깨진다
        assertFalse(PromptBuilder.edit(Block.HERO, true, true, true).contains("theme-bloom"),
                "템플릿 hero 에 백지 테마를 안내하면 템플릿 디자인이 통째로 바뀝니다");

        // ★ 영역을 골라 고칠 때도 안 나온다 — 고르지 않은 영역까지 바뀐다(팔레트와 같은 이유)
        assertFalse(PromptBuilder.edit(Block.HERO, false, false, true).contains("theme-bloom"));
    }

    @Test
    @DisplayName("★ 라우터에 '더 화려하게' = STYLE 예시가 있다 — 없으면 바로 위 EDIT 예시를 베낀다")
    void 라우터가_화려하게를_STYLE_로_보낸다() {
        String r = PromptBuilder.router();
        assertTrue(r.contains("화려하게"), "'화려하게' 예시가 없으면 분위기 요청이 EDIT 으로 떨어집니다");

        // 짝이 되는 EDIT 예시보다 **뒤**에 와야 한다. 모델은 마지막 예시를 제일 무겁게 읽는다
        assertTrue(r.indexOf("친절하게 다듬어줘") < r.indexOf("화려하게 해줘"));

        // 여러 영역을 한 번에 STYLE 로 보내는 꼴을 보여 준다
        assertTrue(r.contains("{\"op\":\"STYLE\",\"target\":\"hero\",\"content\":null},"
                + "{\"op\":\"STYLE\",\"target\":\"steps\",\"content\":null}"), r);

        // op 설명 자체도 넓혔다
        assertTrue(r.contains("눈에 띄게"));
    }
}
