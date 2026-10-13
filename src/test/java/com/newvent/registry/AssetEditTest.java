package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이미지 요청을 <b>모델 없이</b> 처리하는 길.
 *
 * ★ 여기 있는 거의 모든 테스트는 **실제로 난 사고**에서 나왔다.
 *   전부 "모델이 영역을 통째로 다시 쓴다" 라는 한 가지에서 나왔고,
 *   그래서 고치는 방법도 하나다 — 확실한 요청이면 모델을 안 부른다.
 */
class AssetEditTest {

    private static final String U1 = "https://cdn.newvent.test/assets/a1";
    private static final String U2 = "https://cdn.newvent.test/assets/a2";

    private static final List<Asset.Ref> REFS = List.of(
            new Asset.Ref(U1, "벚꽃", 0, Asset.Tone.LIGHT),
            new Asset.Ref(U2, "로고", 0, Asset.Tone.DARK));

    private static String page() {
        return "<div class=\"ev-container event-page\">"
                + "<section data-block=\"hero\"><h1>봄맞이 축제</h1><p>소개 문구입니다</p></section>"
                + "<section data-block=\"benefits\"><ul>"
                + "<li><span class=\"benefit-icon\">🎁</span>커피 쿠폰</li>"
                + "<li><span class=\"benefit-icon\">🎉</span>굿즈</li></ul></section>"
                + "<section data-block=\"cta\"><h2>지금 참여</h2></section></div>";
    }

    private static Document apply(String html, String request) {
        AssetEdit.Plan plan = AssetEdit.parse(request, REFS.size()).orElseThrow();
        return Jsoup.parseBodyFragment(AssetEdit.apply(html, plan, REFS));
    }

    /* ── 읽기 ───────────────────────────────────────────────────── */

    @Test
    @DisplayName("몇 번 · 어떤 역할 · 어느 영역을 읽는다")
    void 읽기() {
        assertEquals(new AssetEdit.Plan(1, Asset.BG, Block.HERO, false, false),
                AssetEdit.parse("1번 이미지를 배경으로 깔아줘", 2).orElseThrow());
        assertEquals(new AssetEdit.Plan(2, Asset.PAGE, Block.HERO, false, false),
                AssetEdit.parse("2번 사진을 페이지 전체 배경으로 해줘", 2).orElseThrow());
        assertEquals(new AssetEdit.Plan(2, Asset.BG, Block.CTA, false, false),
                AssetEdit.parse("참여 버튼 영역 배경에 2번 이미지를 넣어줘", 2).orElseThrow());
        assertEquals(new AssetEdit.Plan(1, Asset.ICON, Block.BENEFITS, false, false),
                AssetEdit.parse("1번 이미지를 혜택 아이콘으로 넣어줘", 2).orElseThrow());
    }

    // ★ "페이지 전체 배경" 이 "배경" 보다 먼저 걸려야 한다. 순서가 뒤집히면 영역 배경이 된다
    @Test
    @DisplayName("페이지 전체 배경이 영역 배경보다 먼저 걸린다")
    void 역할_우선순위() {
        assertEquals(Asset.PAGE, AssetEdit.parse("1번 이미지를 전체 배경으로", 2).orElseThrow().role());
        assertEquals(Asset.BG, AssetEdit.parse("1번 이미지를 배경으로", 2).orElseThrow().role());
    }

    @Test
    @DisplayName("한글 서수도 읽는다")
    void 서수() {
        assertEquals(1, AssetEdit.parse("첫번째 이미지를 배경으로 깔아줘", 2).orElseThrow().index());
        assertEquals(2, AssetEdit.parse("두 번째 이미지를 배경으로 깔아줘", 2).orElseThrow().index());
    }

    // ★★ 확실할 때만 가로챈다. 모호한 요청은 모델이 맡는 게 맞다 —
    //   여기서 억지로 읽으면 엉뚱한 자리에 꽂고 관리자는 왜 그런지 모른다.
    @Test
    @DisplayName("★ 확실하지 않으면 비켜선다")
    void 비켜서기() {
        // 이미지 얘기가 아니다 — "배경" 이라는 말만 보고 가로채면 안 된다
        assertTrue(AssetEdit.parse("배경색을 초록색으로 바꿔줘", 2).isEmpty());
        assertTrue(AssetEdit.parse("전체 색을 초록색으로 바꿔줘", 2).isEmpty());
        // 어디에 넣을지 안 말했다
        assertTrue(AssetEdit.parse("1번 이미지 넣어줘", 2).isEmpty());
        // 몇 번인지 안 말했고 빼는 것도 아니다
        assertTrue(AssetEdit.parse("이미지를 배경으로 깔아줘", 2).isEmpty());
        // 없는 번호 — 지어내 꽂지 않는다
        assertTrue(AssetEdit.parse("5번 이미지를 배경으로 깔아줘", 2).isEmpty());
        // 그 영역에 못 쓰는 역할
        assertTrue(AssetEdit.parse("혜택 영역 배경에 1번 이미지", 2).isEmpty());
    }

    /* ── 적용 ───────────────────────────────────────────────────── */

    // ★ 제보: "1번을 배경으로" 했더니 색과 문구가 같이 바뀌었다
    @Test
    @DisplayName("★ 이미지만 들어가고 문구는 한 글자도 안 바뀐다")
    void 문구는_그대로() {
        Document d = apply(page(), "1번 이미지를 배경으로 깔아줘");

        assertEquals(1, d.select("section[data-block=hero] img.ev-asset-bg").size());
        assertEquals("봄맞이 축제", d.selectFirst("section[data-block=hero] h1").text());
        assertEquals("소개 문구입니다", d.selectFirst("section[data-block=hero] p").text());
    }

    // ★ 제보: 배경으로 넣어 달라고 했는데 혜택 아이콘 자리에도 들어갔다.
    //   모델에게 영역마다 같은 안내가 나가서 각자 넣은 것이었다.
    @Test
    @DisplayName("★ 말한 영역에만 들어간다")
    void 말한_영역에만() {
        Document d = apply(page(), "1번 이미지를 배경으로 깔아줘");

        assertEquals(0, d.select("section[data-block=benefits] img").size());
        assertEquals(0, d.select("section[data-block=cta] img").size());
    }

    // ★ 제보: 그림을 넣어 둔 뒤 배경을 넣으면 그림이 사라졌다
    @Test
    @DisplayName("★ 다른 역할의 이미지는 건드리지 않는다")
    void 다른_역할은_남는다() {
        String withArt = apply(page(), "2번 이미지를 제목 옆 그림으로 넣어줘").body().html();
        Document d = apply(withArt, "1번 이미지를 배경으로 깔아줘");

        assertEquals(1, d.select("img.ev-asset-art").size());
        assertEquals(1, d.select("img.ev-asset-bg").size());
        assertEquals(U1, d.selectFirst("img.ev-asset-bg").attr("src"));
    }

    // ★ 같은 역할은 바꿔 끼운다. 한 영역에 배경이 둘이면 어느 쪽이 보이는지 아무도 모른다
    @Test
    @DisplayName("같은 역할은 바꿔 끼운다")
    void 같은_역할은_교체() {
        String first = apply(page(), "1번 이미지를 배경으로 깔아줘").body().html();
        Document d = apply(first, "2번 이미지를 배경으로 깔아줘");

        assertEquals(1, d.select("img.ev-asset-bg").size());
        assertEquals(U2, d.selectFirst("img.ev-asset-bg").attr("src"));
    }

    // ★ 제보: "배경에 이미지 없애줘" 했더니 문구까지 바뀌었다
    @Test
    @DisplayName("★ 뺄 때도 문구는 그대로다")
    void 빼기() {
        String withBoth = apply(apply(page(), "1번 이미지를 배경으로 깔아줘").body().html(),
                "2번 이미지를 제목 옆 그림으로 넣어줘").body().html();
        Document d = apply(withBoth, "배경에 이미지 없애줘");

        assertEquals(0, d.select("img.ev-asset-bg").size());
        assertEquals(1, d.select("img.ev-asset-art").size(), "빼라고 안 한 그림은 남아야 합니다");
        assertEquals("봄맞이 축제", d.selectFirst("h1").text());
    }

    // ★ 제보: "왼쪽으로" 라고 해도 오른쪽 고정 자리에 들어갔다. 왼쪽 이름이 없었다
    @Test
    @DisplayName("★ 왼쪽으로 말하면 왼쪽에 둔다")
    void 왼쪽() {
        Document d = apply(page(), "1번 이미지를 제목 옆 그림으로 왼쪽에 넣어줘");

        assertTrue(d.selectFirst("img.ev-asset-art").hasClass("ev-asset-art-left"));
    }

    // ★ 아이콘은 이미 있는 아이콘 자리(이모지가 들어가던 span)를 쓴다. 하나만 바꾼다
    @Test
    @DisplayName("아이콘은 첫 자리만 바꾼다")
    void 아이콘() {
        Document d = apply(page(), "1번 이미지를 혜택 아이콘으로 넣어줘");

        assertEquals(1, d.select(".benefit-icon img").size());
        assertEquals("🎉", d.select(".benefit-icon").get(1).text());
    }

    @Test
    @DisplayName("글자색 판정과 설명이 함께 붙는다")
    void 판정과_설명() {
        Document d = apply(page(), "1번 이미지를 배경으로 깔아줘");

        assertTrue(d.selectFirst("img.ev-asset-bg").hasClass("ev-asset-on-light"));
        assertEquals("벚꽃", d.selectFirst("img.ev-asset-bg").attr("alt"));
    }

    /* ── 안전망 ─────────────────────────────────────────────────── */

    // ★★ 모호한 요청은 모델이 맡는데, 그때 모델이 <img> 를 빼고 쓴다.
    //   checkPreserved 는 data-slot · id · class 만 지켜서 이걸 못 잡았다.
    @Test
    @DisplayName("★ 모델이 지운 이미지를 되돌린다")
    void 되돌리기() {
        String before = apply(page(), "1번 이미지를 배경으로 깔아줘")
                .selectFirst("section[data-block=hero]").outerHtml();
        String modelOutput = "<section data-block=\"hero\"><h1>봄맞이 축제</h1><p>소개 문구입니다</p></section>";

        Document d = Jsoup.parseBodyFragment(AssetEdit.restore(before, modelOutput, Block.HERO));

        assertEquals(1, d.select("img.ev-asset-bg").size());
        assertEquals(U1, d.selectFirst("img.ev-asset-bg").attr("src"));
    }

    @Test
    @DisplayName("모델이 지키면 건드리지 않는다")
    void 되돌릴_게_없으면() {
        String before = apply(page(), "1번 이미지를 배경으로 깔아줘")
                .selectFirst("section[data-block=hero]").outerHtml();
        String kept = "<section data-block=\"hero\">"
                + "<img class=\"ev-asset ev-asset-bg\" src=\"" + U1 + "\"><h1>새 제목</h1></section>";

        assertEquals(1, Jsoup.parseBodyFragment(AssetEdit.restore(before, kept, Block.HERO))
                .select("img.ev-asset-bg").size());
    }

    @Test
    @DisplayName("원래 이미지가 없었으면 아무 일도 안 한다")
    void 원래_없었으면() {
        String plain = "<section data-block=\"hero\"><h1>제목</h1></section>";

        assertEquals(plain, AssetEdit.restore(plain, plain, Block.HERO));
    }

    @Test
    @DisplayName("빈 요청에는 비켜선다")
    void 빈_요청() {
        assertTrue(AssetEdit.parse(null, 2).isEmpty());
        assertTrue(AssetEdit.parse("   ", 2).isEmpty());
        assertFalse(AssetEdit.parse("1번 이미지를 배경으로", 0).isPresent());
    }

    @Test
    @DisplayName("없는 영역이면 페이지를 그대로 둔다")
    void 없는_영역() {
        String onlyCta = "<div class=\"ev-container\"><section data-block=\"cta\"><h2>참여</h2></section></div>";
        Optional<AssetEdit.Plan> plan = AssetEdit.parse("1번 이미지를 제목 옆 그림으로 넣어줘", 2);

        assertEquals(onlyCta, AssetEdit.apply(onlyCta, plan.orElseThrow(), REFS));
    }
}
