package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자가 올린 이미지 — 주소는 서버가 쥐고, 배치 이름은 블록별로 거른다.
 *
 * ★ 모델이 틀릴 수 있는 건 "어느 블록에 어떤 배치" 둘뿐이다. 주소는 받아 적기만 한다.
 *   그래서 여기서 재는 것도 그 둘과, 주소를 지어냈을 때다.
 */
class AssetTest {

    private static final String OK = "https://cdn.newvent.test/assets/a1b2";
    private static final List<Asset.Ref> ALLOW = List.of(new Asset.Ref(OK, null, 0, Asset.Tone.LIGHT));

    private static Element clean(String html) {
        return Jsoup.parseBodyFragment(BlockValidator.sanitizeGenerated(html, ALLOW)).body();
    }

    private static String hero(String inner) {
        return "<section data-block=\"hero\">" + inner + "<h1>제목</h1></section>";
    }

    @Test
    @DisplayName("올린 이미지는 배치 이름과 함께 남는다")
    void 올린_이미지는_남는다() {
        Element b = clean(hero("<img class=\"ev-asset ev-asset-bg\" src=\"" + OK + "\" alt=\"\">"));

        assertEquals(1, b.select("img").size());
        assertTrue(b.selectFirst("img").hasClass("ev-asset-bg"));
        assertEquals(OK, b.selectFirst("img").attr("src"));
    }

    // ★★ 이게 지금 열려 있던 구멍이다. Safelist.relaxed 가 img[src] 에 http/https 를
    //   허용해서, 모델이 지어낸 바깥 주소가 그대로 게시됐다 — 추적 픽셀이든 뭐든.
    @Test
    @DisplayName("★ 허용 목록에 없는 주소는 img 째로 지운다")
    void 바깥_주소는_지운다() {
        assertEquals(0, clean(hero("<img src=\"https://evil.example/track.gif\">")).select("img").size());
        assertEquals(0, clean(hero("<img class=\"ev-asset ev-asset-bg\" src=\"https://cdn.newvent.test/assets/zzz\">"))
                .select("img").size());
    }

    // ★ 기본값이 "이미지를 안 받는다" 여야 한다. 업로드가 붙기 전까지 구멍이 닫혀 있어야 하고,
    //   호출하는 쪽이 허용 목록을 깜빡해도 조용히 열리면 안 된다.
    @Test
    @DisplayName("허용 목록을 안 주면 이미지를 아예 안 받는다")
    void 기본값은_전부_거절() {
        String out = BlockValidator.sanitizeGenerated(hero("<img class=\"ev-asset ev-asset-bg\" src=\"" + OK + "\">"));

        assertEquals(0, Jsoup.parseBodyFragment(out).select("img").size());
    }

    // ★ ev-asset-* 는 v- 도 t- 도 아니라서 cleanLooks 를 그냥 지나간다. 여기서 안 거르면
    //   CSS 없는 이름이 저장되고, 다음 수정에서 모델이 그걸 보고 따라 쓴다.
    @Test
    @DisplayName("지어낸 배치 이름은 img 째로 지운다")
    void 지어낸_배치_이름() {
        assertEquals(0, clean(hero("<img class=\"ev-asset ev-asset-huge\" src=\"" + OK + "\">")).select("img").size());
    }

    // ★ 한때 select("img[class]") 였다 — class 가 **아예 없는** 이미지는 선택조차 안 돼서
    //   허용된 주소이기만 하면 살아남았다. 자리잡는 CSS 가 없으니 본문에 원본 크기로 박힌다.
    @Test
    @DisplayName("배치 이름이 없는 맨 이미지도 지운다")
    void 맨_이미지() {
        assertEquals(0, clean(hero("<img src=\"" + OK + "\">")).select("img").size());
    }

    @Test
    @DisplayName("그 블록에 쓸 수 없는 배치도 지운다 - 로고는 hero 전용")
    void 블록에_안_맞는_배치() {
        String benefits = "<section data-block=\"benefits\">"
                + "<img class=\"ev-asset ev-asset-logo\" src=\"" + OK + "\">"
                + "<ul><li>가</li><li>나</li></ul></section>";

        assertEquals(0, clean(benefits).select("img").size());
    }

    @Test
    @DisplayName("혜택 아이콘은 benefits 에서 살아남는다")
    void 혜택_아이콘() {
        String benefits = "<section data-block=\"benefits\"><ul><li>"
                + "<div class=\"benefit-icon\"><img class=\"ev-asset ev-asset-icon\" src=\"" + OK + "\"></div>가"
                + "</li><li>나</li></ul></section>";

        assertTrue(clean(benefits).selectFirst("img").hasClass("ev-asset-icon"));
    }

    // ★ 배경 두 장은 겹쳐서 아래 것이 안 보인다. 변형 · 장식과 같은 규칙이다 — 먼저 쓴 것을 남긴다.
    @Test
    @DisplayName("같은 배치를 두 번 쓰면 하나만 남는다")
    void 같은_배치는_하나만() {
        Element b = clean(hero("<img class=\"ev-asset ev-asset-bg\" src=\"" + OK + "\">"
                + "<img class=\"ev-asset ev-asset-bg\" src=\"" + OK + "\">"));

        assertEquals(1, b.select("img").size());
    }

    @Test
    @DisplayName("onerror 같은 핸들러는 애초에 못 들어온다")
    void 핸들러는_막힌다() {
        String out = BlockValidator.sanitizeGenerated(
                hero("<img class=\"ev-asset ev-asset-bg\" src=\"" + OK + "\" onerror=\"alert(1)\">"), ALLOW);

        assertFalse(out.contains("onerror"));
        assertEquals(1, Jsoup.parseBodyFragment(out).select("img").size());
    }

    /* ── 글자색 판정 ───────────────────────────────────────────── */

    // ★ ImageProbe 가 올릴 때 한 번 재 둔 값이다. 안 쓰면 재고 저장만 하고 버리는 셈이고,
    //   밝은 그림에도 흰 글씨 + 덮개가 가서 사진이 갈색으로 뭉개진다.
    @Test
    @DisplayName("재 둔 판정을 class 로 박는다 - 덮개는 MIXED 에만")
    void 글자색_판정() {
        assertTrue(toned(Asset.Tone.LIGHT, 0).hasClass("ev-asset-on-light"));
        assertTrue(toned(Asset.Tone.DARK, 0).hasClass("ev-asset-on-dark"));
        assertTrue(toned(Asset.Tone.MIXED, .35).hasClass("ev-asset-on-mixed"));

        assertFalse(toned(Asset.Tone.LIGHT, 0).className().contains("dim-"));
        assertFalse(toned(Asset.Tone.DARK, 0).className().contains("dim-"));
        assertTrue(toned(Asset.Tone.MIXED, .35).hasClass("ev-asset-dim-35"));
        assertTrue(toned(Asset.Tone.MIXED, .68).hasClass("ev-asset-dim-68"));
        assertTrue(toned(Asset.Tone.MIXED, .55).hasClass("ev-asset-dim-55"));
    }

    // ★ 서버만 붙이는 이름이다. 모델이 따라 쓰면 CSS 에 없는 값이 들어온다
    @Test
    @DisplayName("모델이 쓴 판정 class 는 떨어진다")
    void 지어낸_판정() {
        Element b = clean(hero("<img class=\"ev-asset-bg ev-asset-on-light ev-asset-dim-90\" src=\"" + OK + "\">"));

        assertFalse(b.selectFirst("img").className().contains("dim-90"));
    }

    // ★ 글자가 올라가는 자리에만. 그림 · 아이콘 · 로고 · 띠 위에는 글자가 안 온다
    @Test
    @DisplayName("배경이 아니면 판정을 안 붙인다")
    void 배경만_칠한다() {
        String html = BlockValidator.sanitizeGenerated(hero("<img class=\"ev-asset-art\" src=\"" + OK + "\">"),
                List.of(new Asset.Ref(OK, null, 0, Asset.Tone.DARK)));

        assertFalse(Jsoup.parseBodyFragment(html).selectFirst("img").className().contains("on-"));
    }

    // ★ V20 이전에 올라온 행은 tone 이 없을 수 있다. 틀린 색을 박느니 아무것도 안 붙인다
    @Test
    @DisplayName("판정이 없으면 아무것도 안 붙인다")
    void 판정_없음() {
        String html = BlockValidator.sanitizeGenerated(hero("<img class=\"ev-asset-bg\" src=\"" + OK + "\">"),
                List.of(new Asset.Ref(OK, null, 0, null)));

        assertFalse(Jsoup.parseBodyFragment(html).selectFirst("img").className().contains("on-"));
    }

    private static Element toned(Asset.Tone tone, double scrim) {
        String html = BlockValidator.sanitizeGenerated(hero("<img class=\"ev-asset-bg\" src=\"" + OK + "\">"),
                List.of(new Asset.Ref(OK, null, scrim, tone)));
        return Jsoup.parseBodyFragment(html).selectFirst("img");
    }

    /* ── 페이지 전체 배경 ───────────────────────────────────────── */

    // ★ 모델은 루트를 못 만진다. hero 안에 쓰게 하고 서버가 옮긴다 — 팔레트 · 테마와 같은 길
    @Test
    @DisplayName("페이지 배경은 hero 에서만 받는다")
    void 페이지_배경_자리() {
        assertTrue(Asset.of(Block.HERO).contains(Asset.PAGE));
        assertFalse(Asset.of(Block.CTA).contains(Asset.PAGE));
        assertFalse(Asset.of(Block.BENEFITS).contains(Asset.PAGE));
    }


    /* ── 프롬프트 안내 ─────────────────────────────────────────── */

    private static final List<Asset.Ref> REFS = List.of(
            new Asset.Ref(OK, "강아지 일러스트", 0, Asset.Tone.LIGHT),
            new Asset.Ref("https://cdn.newvent.test/assets/a2b3", null, 0, Asset.Tone.DARK));

    // ★ "이미지를 넣을 수 있다" 만 알려 주면 모델이 주소를 지어낸다. 올린 게 없으면 한 줄도 안 나가야 한다.
    @Test
    @DisplayName("★ 올린 이미지가 없으면 안내가 한 줄도 안 나간다")
    void 이미지가_없으면_안내도_없다() {
        String p = PromptBuilder.edit(Block.HERO, false, true, true, false, List.of());

        assertFalse(p.contains("올린 이미지"));
        assertFalse(p.contains("ev-asset"));
    }

    @Test
    @DisplayName("올린 이미지의 주소와 설명이 그대로 실린다")
    void 주소와_설명이_실린다() {
        String p = PromptBuilder.edit(Block.HERO, false, true, true, false, REFS);

        assertTrue(p.contains(OK));
        assertTrue(p.contains("강아지 일러스트"));
        assertTrue(p.contains("주소를 지어내거나 바꾸지 마라"));
    }

    // ★ hero 에 아이콘을 안내하면 모델이 그걸 고른다. 블록마다 쓸 수 있는 것만 보여 준다.
    @Test
    @DisplayName("★ 그 블록에 쓸 수 있는 배치만 안내한다")
    void 블록에_맞는_배치만_안내() {
        String hero = PromptBuilder.edit(Block.HERO, false, true, true, false, REFS);
        assertTrue(hero.contains("ev-asset-bg"));
        assertTrue(hero.contains("ev-asset-logo"));
        assertFalse(hero.contains("ev-asset-icon"));

        String benefits = PromptBuilder.edit(Block.BENEFITS, false, false, false, false, REFS);
        assertTrue(benefits.contains("ev-asset-icon"));
        assertFalse(benefits.contains("ev-asset-bg"));
    }

    // ★ 변형 · 테마와 다르다. 이미지는 템플릿 블록에도 걸린다 — 95-assets 의 CSS 는 .ev-block 을 안 가린다.
    @Test
    @DisplayName("템플릿 블록에도 이미지는 안내한다")
    void 템플릿에도_이미지는_된다() {
        assertTrue(PromptBuilder.edit(Block.HERO, true, false, false, false, REFS).contains("ev-asset-bg"));
    }

    @Test
    @DisplayName("블록마다 쓸 수 있는 배치가 레지스트리에 있다")
    void 블록별_배치_목록() {
        assertTrue(Asset.of(Block.HERO).contains(Asset.BG));
        assertTrue(Asset.of(Block.HERO).contains(Asset.LOGO));
        assertFalse(Asset.of(Block.HERO).contains(Asset.ICON));

        assertTrue(Asset.of(Block.BENEFITS).contains(Asset.ICON));
        assertFalse(Asset.of(Block.BENEFITS).contains(Asset.BG));
    }
}
