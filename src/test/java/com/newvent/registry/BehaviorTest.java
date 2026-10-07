package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 동작(data-behavior) — 정화 · 보존.
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
}
