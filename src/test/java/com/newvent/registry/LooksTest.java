package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자가 화면에서 고르는 배치 · 모양 — 모델 없이 서버가 class 만 바꾼다.
 */
class LooksTest {

    private static final Map<Block, Variant> NO_VARIANTS = Map.of();

    /** 내용 영역 5개 — 소개 · 숫자 · 혜택(3) · 참여 대상 · FAQ */
    private static final String PAGE = "<div class=\"ev-container event-page theme-basic\">"
            + "<section data-block=\"hero\"><h1>t</h1></section>"
            + "<section data-block=\"intro\"><p>x</p></section>"
            + "<section data-block=\"stats\"><ul><li><strong>1</strong></li><li><strong>2</strong></li></ul></section>"
            + "<section data-block=\"benefits\" class=\"v-surface-tint\"><ul><li>a</li><li>b</li><li>c</li></ul></section>"
            + "<section data-block=\"audience\"><ul><li>a</li></ul></section>"
            + "<section data-block=\"faq\"><dl><dt>q</dt><dd>a</dd></dl></section>"
            + "<section data-block=\"notices\" class=\"ev-block\"><p>n</p></section>"
            + "<section data-block=\"cta\"><a href=\"#\" class=\"btn\">go</a></section></div>";

    /** 내용 영역 1개 */
    private static final String SMALL = "<div class=\"ev-container event-page theme-basic\">"
            + "<section data-block=\"hero\"><h1>t</h1></section>"
            + "<section data-block=\"benefits\"><ul><li>a</li><li>b</li></ul></section>"
            + "<section data-block=\"cta\"><a href=\"#\" class=\"btn\">go</a></section></div>";

    private static final String TEMPLATE = "<div class=\"ev-container event-page theme-sports\">"
            + "<section data-block=\"hero\" class=\"ev-block\"><h1>t</h1></section>"
            + "<section data-block=\"benefits\" class=\"ev-block\"><ul><li>a</li><li>b</li><li>c</li></ul></section>"
            + "<section data-block=\"steps\" class=\"ev-block\"><ol><li>a</li></ol></section>"
            + "<section data-block=\"cta\" class=\"ev-block\"><a href=\"#\" class=\"btn\">go</a></section></div>";

    private static Element el(String html, String css) {
        return Jsoup.parseBodyFragment(html).selectFirst(css);
    }

    @Test
    @DisplayName("배치를 고르면 루트에 그 class 하나만 남는다. 기본으로 되돌리면 class 가 사라진다")
    void 배치를_건다() {
        String bento = Looks.apply(PAGE, Layout.BENTO, NO_VARIANTS);
        assertTrue(el(bento, ".ev-container").hasClass("layout-bento"), bento);
        assertTrue(el(bento, ".ev-container").hasClass("theme-basic"), "테마가 사라졌습니다: " + bento);

        String wide = Looks.apply(bento, Layout.WIDE, NO_VARIANTS);
        assertTrue(el(wide, ".ev-container").hasClass("layout-wide"), wide);
        assertFalse(el(wide, ".ev-container").hasClass("layout-bento"), wide);

        String stack = Looks.apply(wide, Layout.STACK, NO_VARIANTS);
        assertFalse(el(stack, ".ev-container").className().contains("layout-"), stack);
    }

    @Test
    @DisplayName("영역이 모자란 배치 · 이미 그 배치 · 템플릿 페이지는 이유를 돌려준다")
    void 못_거는_배치() {
        Looks.Blocked few = assertThrows(Looks.Blocked.class,
                () -> Looks.apply(SMALL, Layout.BENTO, NO_VARIANTS));
        assertTrue(few.getMessage().contains("4개 이상"), few.getMessage());

        Looks.Blocked tpl = assertThrows(Looks.Blocked.class,
                () -> Looks.apply(TEMPLATE, Layout.WIDE, NO_VARIANTS));
        assertTrue(tpl.getMessage().contains("템플릿"), tpl.getMessage());
    }

    @Test
    @DisplayName("영역 모양을 바꾸면 그 블록의 모양 class 만 바뀐다. 배경(v-surface-*)은 그대로다")
    void 모양을_바꾼다() {
        String grid = Looks.apply(PAGE, null, Map.of(Block.BENEFITS, Variant.BENEFITS_GRID));
        Element sec = el(grid, "[data-block=benefits]");
        assertTrue(sec.hasClass("v-benefits-grid"), grid);
        assertTrue(sec.hasClass("v-surface-tint"), "배경이 사라졌습니다: " + grid);

        String bento = Looks.apply(grid, null, Map.of(Block.BENEFITS, Variant.BENEFITS_BENTO));
        assertTrue(el(bento, "[data-block=benefits]").hasClass("v-benefits-bento"), bento);
        assertFalse(el(bento, "[data-block=benefits]").hasClass("v-benefits-grid"), bento);

        // 모양을 떼면(null) 모양 class 만 사라진다
        Map<Block, Variant> none = new java.util.HashMap<>();
        none.put(Block.BENEFITS, null);
        String plain = Looks.apply(bento, null, none);
        assertFalse(el(plain, "[data-block=benefits]").className().contains("v-benefits-"), plain);
        assertTrue(el(plain, "[data-block=benefits]").hasClass("v-surface-tint"), plain);

        // 첫 번째 모양도 class 를 붙인다 — hero 의 첫 모양은 class 가 없는 것과 다르게 그려진다
        String hero = Looks.apply(PAGE, null, Map.of(Block.HERO, Variant.HERO_CENTER));
        assertTrue(el(hero, "[data-block=hero]").hasClass("v-hero-center"), hero);
    }

    @Test
    @DisplayName("항목이 모자란 모양 · 다른 블록의 모양 · 없는 영역은 이유를 돌려준다")
    void 못_쓰는_모양() {
        // 숫자가 2개뿐이다 — 벤토는 3개 이상
        Looks.Blocked few = assertThrows(Looks.Blocked.class,
                () -> Looks.apply(PAGE, null, Map.of(Block.STATS, Variant.STATS_BENTO)));
        assertTrue(few.getMessage().contains("3개 이상"), few.getMessage());

        assertThrows(Looks.Blocked.class,
                () -> Looks.apply(PAGE, null, Map.of(Block.STATS, Variant.BENEFITS_GRID)));
        assertThrows(Looks.Blocked.class,
                () -> Looks.apply(PAGE, null, Map.of(Block.PRIZE, Variant.PRIZE_CARDS)));
        assertThrows(Looks.Blocked.class,
                () -> Looks.apply(TEMPLATE, null, Map.of(Block.BENEFITS, Variant.BENEFITS_GRID)));
    }

    @Test
    @DisplayName("선택지 — 지금 상태와, 못 고르는 것의 이유를 같이 준다")
    void 선택지() {
        Looks.Catalog c = Looks.catalog(PAGE);
        assertEquals("layout-stack", c.layout());
        assertFalse(c.template());
        assertEquals(Layout.all().size(), c.layouts().size());
        assertNull(option(c, "layout-bento").blocked());
        assertEquals("벤토", option(c, "layout-bento").label());
        // 유의사항은 서버 소유라 목록에 없다. 순서는 페이지에 보이는 순서다
        assertEquals(java.util.List.of("hero", "intro", "stats", "benefits", "audience", "faq", "cta"),
                java.util.List.copyOf(c.blocks().keySet()));

        Looks.BlockOption stats = c.blocks().get("stats");
        assertEquals("", stats.variant());
        assertNotNull(stats.variants().stream().filter(v -> v.cssClass().equals("v-stats-bento"))
                .findFirst().orElseThrow().blocked());

        // 영역이 모자란 페이지 — 벤토에 이유가 붙는다
        assertNotNull(option(Looks.catalog(SMALL), "layout-bento").blocked());

        // 고른 뒤의 상태가 그대로 읽힌다
        Looks.Catalog after = Looks.catalog(Looks.apply(PAGE, Layout.WIDE,
                Map.of(Block.BENEFITS, Variant.BENEFITS_GRID)));
        assertEquals("layout-wide", after.layout());
        assertEquals("v-benefits-grid", after.blocks().get("benefits").variant());

        // 템플릿 페이지 — 배치는 전부 막힌다
        Looks.Catalog tpl = Looks.catalog(TEMPLATE);
        assertTrue(tpl.template());
        assertNotNull(option(tpl, "layout-wide").blocked());
    }

    private static Looks.LayoutOption option(Looks.Catalog c, String cssClass) {
        return c.layouts().stream().filter(l -> l.cssClass().equals(cssClass)).findFirst().orElseThrow();
    }
}
