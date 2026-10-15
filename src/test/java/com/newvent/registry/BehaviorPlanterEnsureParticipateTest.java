package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link BehaviorPlanter#ensureParticipate} — 옛 페이지에 참여 이름표를 심어 주는 보수층.
 *
 * ★ 왜 테스트하나 — 운영에 머지된 14개 전부 이 이름표가 없었다.
 *   이 함수가 돌지 않으면 bindGame 이 자리를 못 잡아 게임 UI 가 안 그려지고,
 *   프론트도 「참여 이름표가 있는지」로 패널을 띄울지 정하므로 참여 자체가 막힌다.
 */
class BehaviorPlanterEnsureParticipateTest {

    /**
     * 운영에 머지돼 있는 옛 페이지와 **같은 모양** — cta 블록은 있고 이름표는 없다.
     * (실측: 게시된 14개 전부 data-behavior="participate" 0건)
     */
    private static final String LEGACY_PAGE =
            "<section data-block=\"hero\"><h1>제목</h1></section>"
            + "<section data-block=\"cta\" class=\"ev-block\">"
            + "<button class=\"cta-btn\">참여하기</button></section>";

    @Test
    @DisplayName("★ 옛 페이지 — cta 블록 첫 버튼에 참여 이름표를 심는다")
    void 옛_페이지에_이름표를_심는다() {
        String out = BehaviorPlanter.ensureParticipate(LEGACY_PAGE);

        Document doc = Jsoup.parseBodyFragment(out);
        assertEquals(1, doc.select("[data-behavior=participate]").size());
        assertEquals("button", doc.selectFirst("[data-behavior=participate]").tagName());
        assertEquals("참여하기", doc.selectFirst("[data-behavior=participate]").text());
    }

    @Test
    @DisplayName("템플릿의 <a> 도 참여 버튼으로 쓴다 — 백지 생성이든 템플릿이든 같은 자리")
    void a_태그에도_심는다() {
        String html = "<section data-block=\"cta\"><a href=\"#\" class=\"btn\">응모하기</a></section>";

        String out = BehaviorPlanter.ensureParticipate(html);

        Document doc = Jsoup.parseBodyFragment(out);
        assertEquals("a", doc.selectFirst("[data-behavior=participate]").tagName());
    }

    @Test
    @DisplayName("이미 이름표가 있으면 문자열을 그대로 돌려준다 — 새 페이지는 비용이 없다")
    void 이미_있으면_그대로다() {
        String withLabel = "<section data-block=\"cta\">"
                + "<button data-behavior=\"participate\">참여하기</button></section>";

        assertSame(withLabel, BehaviorPlanter.ensureParticipate(withLabel),
                "같은 참조로 돌려주면 Jsoup 파싱이 안 일어난 것이다");
    }

    @Test
    @DisplayName("cta 블록이 없으면 손대지 않는다 — 심을 자리가 없다")
    void 심을_자리가_없으면_그대로다() {
        String noCta = "<section data-block=\"hero\"><h1>제목</h1></section>";

        assertSame(noCta, BehaviorPlanter.ensureParticipate(noCta));
    }

    @Test
    @DisplayName("cta 블록은 있지만 버튼이 없으면 손대지 않는다")
    void 버튼이_없으면_그대로다() {
        String empty = "<section data-block=\"cta\"><p>준비 중</p></section>";

        String out = BehaviorPlanter.ensureParticipate(empty);

        assertFalse(Jsoup.parseBodyFragment(out).select("[data-behavior=participate]").size() > 0);
    }

    @Test
    @DisplayName("null · 공백은 그대로 돌려준다")
    void 빈값은_그대로다() {
        assertEquals(null, BehaviorPlanter.ensureParticipate(null));
        assertEquals("   ", BehaviorPlanter.ensureParticipate("   "));
    }

    @Test
    @DisplayName("cta 블록에 버튼이 여럿이면 첫 것에만 — participate() 와 같은 규칙")
    void 첫_버튼에만_붙인다() {
        String many = "<section data-block=\"cta\">"
                + "<button class=\"primary\">참여하기</button>"
                + "<button class=\"secondary\">자세히</button></section>";

        String out = BehaviorPlanter.ensureParticipate(many);

        Document doc = Jsoup.parseBodyFragment(out);
        assertEquals(1, doc.select("[data-behavior=participate]").size());
        assertTrue(doc.selectFirst("[data-behavior=participate]").hasClass("primary"));
    }

    @Test
    @DisplayName("ensureParticipate 결과를 bindGame 에 넘기면 게임 자리가 심긴다 — 통합 보장")
    void ensureParticipate_뒤에_bindGame이_동작한다() {
        String withLabel = BehaviorPlanter.ensureParticipate(LEGACY_PAGE);
        Document doc = Jsoup.parseBodyFragment(withLabel);

        boolean drawn = BehaviorPlanter.bindGame(doc, "LUCKY_POUCH", java.util.Map.of("pouches",
                java.util.List.of(
                        java.util.Map.of("pouchIndex", 1, "winProbability", 30, "prizeName", "한우세트"),
                        java.util.Map.of("pouchIndex", 2, "winProbability", 10, "prizeName", "쿠폰"))));

        assertTrue(drawn, "옛 페이지도 ensureParticipate 를 거치면 게임이 그려져야 한다");
        assertEquals(2, doc.select(".ev-game-choice").size());
    }
}
