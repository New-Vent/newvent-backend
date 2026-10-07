package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 동작(data-behavior) — 정화 · 보존 · 서버 심기 · 보여 줄 때 묶기 · 문서 조립.
 */
class BehaviorTest {

    private static Element first(String html, String selector) {
        return Jsoup.parseBodyFragment(html).body().selectFirst(selector);
    }

    // ── 정화 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("수정 정화 — 목록 밖 behavior 이름만 걷어내고, 남는 게 없으면 속성째 지운다")
    void 목록_밖_behavior는_지운다() {
        String html = "<section data-block=\"cta\">"
                + "<button class=\"btn\" data-behavior=\"toast hack\">a</button>"
                + "<button class=\"btn\" data-behavior=\"eval\">b</button></section>";

        Document d = Jsoup.parseBodyFragment(BlockValidator.sanitizeEdited(html));

        assertEquals("toast", d.select("button").get(0).attr("data-behavior"));
        assertFalse(d.select("button").get(1).hasAttr("data-behavior"), d.body().html());
    }

    @Test
    @DisplayName("수정 정화 — data-endpoint · data-url · data-method 처럼 허용 목록 밖 속성은 지운다")
    void 금지_속성은_지운다() {
        String html = "<section data-block=\"cta\"><button class=\"btn\" data-behavior=\"participate\""
                + " data-endpoint=\"https://evil.example/collect\" data-url=\"/x\" data-method=\"DELETE\""
                + " data-demo-msg=\"응모\" onclick=\"steal()\">응모</button></section>";

        Element btn = first(BlockValidator.sanitizeEdited(html), "button");

        assertEquals("participate", btn.attr("data-behavior"));
        assertEquals("응모", btn.attr("data-demo-msg"));
        for (String bad : List.of("data-endpoint", "data-url", "data-method", "onclick")) {
            assertFalse(btn.hasAttr(bad), bad + " 가 남았습니다: " + btn.outerHtml());
        }
    }

    @Test
    @DisplayName("생성 정화 — 모델이 만든 behavior 는 전부 지운다")
    void 생성_정화는_behavior를_지운다() {
        String html = "<section data-block=\"cta\"><a href=\"#\" class=\"btn\" data-behavior=\"toast\""
                + " data-target=\"x\">참여</a></section>";

        Element a = first(BlockValidator.sanitizeGenerated(html), "a");

        assertFalse(a.hasAttr("data-behavior"));
        assertFalse(a.hasAttr("data-target"));
    }

    // ── 보존 ──────────────────────────────────────────────────────

    private static final String CTA_BEFORE = "<section class=\"ev-block\" data-block=\"cta\">"
            + "<button class=\"btn\" data-slot=\"cta-link\" data-behavior=\"toast\" data-demo-msg=\"응모\">응모하기</button>"
            + "</section>";

    private static List<String> codes(String after) {
        return BlockValidator.validateEdited(Block.CTA, CTA_BEFORE, after)
                .stream().map(BlockValidator.Failure::code).toList();
    }

    @Test
    @DisplayName("보존 — 수정 결과에서 behavior 가 사라지면 behavior_lost")
    void behavior를_지우면_걸린다() {
        List<String> codes = codes(CTA_BEFORE.replace(" data-behavior=\"toast\"", ""));

        assertTrue(codes.contains("behavior_lost_toast"), codes.toString());
    }

    @Test
    @DisplayName("보존 — 수정 결과에 없던 behavior 가 생기면 behavior_invented")
    void behavior를_만들면_걸린다() {
        List<String> codes = codes(CTA_BEFORE.replace("data-behavior=\"toast\"", "data-behavior=\"toast participate\""));

        assertTrue(codes.contains("behavior_invented_participate"), codes.toString());
    }

    @Test
    @DisplayName("보존 — 동작 없는 <button> 을 새로 만들면 button_invented (눌러도 아무 일이 없는 버튼 방지)")
    void 버튼을_만들면_걸린다() {
        List<String> codes = codes(CTA_BEFORE.replace("</section>", "<button class=\"btn\">투표 1</button></section>"));

        assertTrue(codes.contains("button_invented"), codes.toString());
    }

    @Test
    @DisplayName("보존 — 문구만 고치면 통과한다")
    void 문구만_고치면_통과() {
        String after = CTA_BEFORE.replace("응모하기", "지금 응모하기 🎉");

        assertTrue(BlockValidator.validateEdited(Block.CTA, CTA_BEFORE, after).stream()
                .noneMatch(BlockValidator.Failure::isBlocking));
    }

    // ── 서버 심기 ──────────────────────────────────────────────────

    private static final String GENERATED = "<section data-block=\"hero\"><h1>t</h1></section>"
            + "<section data-block=\"benefits\"><ul><li>a</li></ul></section>"
            + "<section data-block=\"cta\"><a href=\"#\" class=\"btn\">참여</a></section>";

    @Test
    @DisplayName("★ 백지 생성 — 서버가 participate · countdown · 혜택으로 가는 scroll-to 를 심는다")
    void 생성_페이지에_심는다() {
        Document d = Jsoup.parseBodyFragment(BehaviorPlanter.plantGenerated(GENERATED));

        assertEquals("participate", d.selectFirst("[data-block=cta] a.btn").attr("data-behavior"));
        assertNotNull(d.selectFirst("[data-block=hero] .ev-countdown [data-behavior=countdown]"));
        Element jump = d.selectFirst("[data-block=hero] a[data-behavior=scroll-to]");
        assertEquals("ev-benefits", jump.attr("data-target"));
        assertEquals("ev-benefits", d.selectFirst("[data-block=benefits]").id(), "이동 대상에 id 가 없습니다");
    }

    @Test
    @DisplayName("백지 생성 — 두 번 심지 않고, 혜택이 없으면 참여 방법으로, 둘 다 없으면 이동 버튼을 안 만든다")
    void 생성_심기는_멱등() {
        String once = BehaviorPlanter.plantGenerated(GENERATED);
        assertEquals(once, BehaviorPlanter.plantGenerated(once), "두 번 심었습니다");

        String steps = BehaviorPlanter.plantGenerated(GENERATED.replace("benefits", "steps"));
        assertEquals("ev-steps", first(steps, "[data-behavior=scroll-to]").attr("data-target"));

        String heroOnly = BehaviorPlanter.plantGenerated("<section data-block=\"hero\"><h1>t</h1></section>");
        assertNull(first(heroOnly, "[data-behavior=scroll-to]"));
        assertNotNull(first(heroOnly, "[data-behavior=countdown]"));
    }

    @Test
    @DisplayName("백지 생성 — 심은 것은 수정 보존 검사에 걸린다 (hero 의 이동 버튼을 지우면 behavior_lost)")
    void 심은_것은_보존_대상() {
        String hero = BlockValidator.blockOf(BehaviorPlanter.plantGenerated(GENERATED), Block.HERO);
        Document edited = Jsoup.parseBodyFragment(hero);
        edited.select(".ev-jump").remove();

        List<String> codes = BlockValidator.validateEdited(Block.HERO, hero, edited.body().html())
                .stream().map(BlockValidator.Failure::code).toList();

        assertTrue(codes.contains("behavior_lost_scroll-to"), codes.toString());
    }

    // ── 보여 줄 때 묶기 ──────────────────────────────────────────────

    private static final OffsetDateTime ENDS = OffsetDateTime.parse("2026-10-31T23:59:59+09:00");

    @Test
    @DisplayName("묶기 — countdown 에 종료 시각(data-until)을 넣고, 종료일이 없으면 건드리지 않는다")
    void countdown에_종료_시각() {
        Document d = Jsoup.parseBodyFragment(BehaviorPlanter.plantGenerated(GENERATED));
        BehaviorPlanter.bind(d, ENDS);
        assertEquals(ENDS.toInstant().toString(), d.selectFirst("[data-behavior=countdown]").attr("data-until"));

        Document none = Jsoup.parseBodyFragment(BehaviorPlanter.plantGenerated(GENERATED));
        BehaviorPlanter.bind(none, null);
        assertFalse(none.selectFirst("[data-behavior=countdown]").hasAttr("data-until"));
    }

    // ── 문서 조립 (PageShell.standalone) ─────────────────────────────

    private static Document page(String fragment) {
        return Jsoup.parse(PageShell.standalone(fragment, "가을 <축제>", ENDS));
    }

    @Test
    @DisplayName("★ 조립 — 새 방식 페이지는 인라인 <script> 를 지우고 runtime.js 하나만 싣고, 종료 시각을 묶는다")
    void 새_방식_문서() {
        String fragment = "<div class=\"ev-container event-page theme-sale\">"
                + BehaviorPlanter.plantGenerated(GENERATED)
                + "<script>alert(1)</script></div>";

        Document doc = page(fragment);

        assertEquals("가을 <축제>", doc.title());
        assertNotNull(doc.head().selectFirst("link[rel=stylesheet][href=/assets/event.css]"));
        assertTrue(doc.body().hasClass("theme-sale"), "테마가 body 에 없습니다");

        List<Element> scripts = doc.select("script");
        assertEquals(1, scripts.size(), "runtime 하나뿐이어야 합니다");
        assertEquals("/assets/event-runtime.js", scripts.get(0).attr("src"));
        assertEquals(ENDS.toInstant().toString(), doc.selectFirst("[data-behavior=countdown]").attr("data-until"));
    }

    @Test
    @DisplayName("★ 조립 — behavior 이전 페이지는 옛 <script> 와 마크업을 그대로 두고, 묶지도 않는다")
    void 옛_페이지는_그대로() {
        String fragment = "<div class=\"ev-container event-page theme-sale\">"
                + "<section class=\"ev-block\" data-block=\"cta\"><button class=\"cta-btn btn\">go</button></section>"
                + "<script>window.newVentReinit = function () {};</script></div>";

        Document doc = page(fragment);

        List<Element> scripts = doc.select("script");
        assertEquals(2, scripts.size(), "옛 스크립트와 runtime 이 둘 다 있어야 합니다");
        assertEquals("window.newVentReinit = function () {};", scripts.get(0).data());
        assertEquals("/assets/event-runtime.js", scripts.get(1).attr("src"));
        assertNull(doc.selectFirst("[data-behavior]"), "옛 페이지에 behavior 를 붙였습니다");
    }

    @Test
    @DisplayName("조립 — 제목이 없으면 '이벤트'")
    void 빈_값() {
        Document doc = Jsoup.parse(PageShell.standalone("<div class=\"ev-container\"></div>", null, null));

        assertEquals("이벤트", doc.title());
        assertEquals(1, doc.select("script").size());
    }
}
