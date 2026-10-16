package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
            new Asset.Ref(1, U1, "벚꽃", 0, Asset.Tone.LIGHT),
            new Asset.Ref(2, U2, "로고", 0, Asset.Tone.DARK));

    private static String page() {
        return "<div class=\"ev-container event-page\">"
                + "<section data-block=\"hero\"><h1>봄맞이 축제</h1><p>소개 문구입니다</p></section>"
                + "<section data-block=\"benefits\"><ul>"
                + "<li><span class=\"benefit-icon\">🎁</span>커피 쿠폰</li>"
                + "<li><span class=\"benefit-icon\">🎉</span>굿즈</li></ul></section>"
                + "<section data-block=\"cta\"><h2>지금 참여</h2></section></div>";
    }

    private static Document apply(String html, String request) {
        AssetEdit.Plan plan = AssetEdit.parse(request, REFS, html).orElseThrow();
        return Jsoup.parseBodyFragment(AssetEdit.apply(html, plan, REFS));
    }

    /* ── 읽기 ───────────────────────────────────────────────────── */

    @Test
    @DisplayName("몇 번 · 어떤 역할 · 어느 영역을 읽는다")
    void 읽기() {
        assertEquals(new AssetEdit.Plan(1, Asset.BG, Block.HERO, false, false),
                AssetEdit.parse("1번 이미지를 배경으로 깔아줘", REFS, page()).orElseThrow());
        assertEquals(new AssetEdit.Plan(2, Asset.PAGE, Block.HERO, false, false),
                AssetEdit.parse("2번 사진을 페이지 전체 배경으로 해줘", REFS, page()).orElseThrow());
        assertEquals(new AssetEdit.Plan(2, Asset.BG, Block.CTA, false, false),
                AssetEdit.parse("참여 버튼 영역 배경에 2번 이미지를 넣어줘", REFS, page()).orElseThrow());
        assertEquals(new AssetEdit.Plan(1, Asset.ICON, Block.BENEFITS, false, false),
                AssetEdit.parse("1번 이미지를 혜택 아이콘으로 넣어줘", REFS, page()).orElseThrow());
    }

    // ★ "페이지 전체 배경" 이 "배경" 보다 먼저 걸려야 한다. 순서가 뒤집히면 영역 배경이 된다
    @Test
    @DisplayName("페이지 전체 배경이 영역 배경보다 먼저 걸린다")
    void 역할_우선순위() {
        assertEquals(Asset.PAGE, AssetEdit.parse("1번 이미지를 전체 배경으로", REFS, page()).orElseThrow().role());
        assertEquals(Asset.BG, AssetEdit.parse("1번 이미지를 배경으로", REFS, page()).orElseThrow().role());
    }

    @Test
    @DisplayName("한글 서수도 읽는다")
    void 서수() {
        assertEquals(1, AssetEdit.parse("첫번째 이미지를 배경으로 깔아줘", REFS, page()).orElseThrow().seq());
        assertEquals(2, AssetEdit.parse("두 번째 이미지를 배경으로 깔아줘", REFS, page()).orElseThrow().seq());
    }

    // ★★ 확실할 때만 가로챈다. 모호한 요청은 모델이 맡는 게 맞다 —
    //   여기서 억지로 읽으면 엉뚱한 자리에 꽂고 관리자는 왜 그런지 모른다.
    @Test
    @DisplayName("★ 확실하지 않으면 비켜선다")
    void 비켜서기() {
        // 이미지 얘기가 아니다 — "배경" 이라는 말만 보고 가로채면 안 된다
        assertTrue(AssetEdit.parse("배경색을 초록색으로 바꿔줘", REFS, page()).isEmpty());
        assertTrue(AssetEdit.parse("전체 색을 초록색으로 바꿔줘", REFS, page()).isEmpty());
        // 어디에 넣을지 안 말했다
        assertTrue(AssetEdit.parse("1번 이미지 넣어줘", REFS, page()).isEmpty());
        // 몇 번인지 안 말했고 빼는 것도 아니다
        assertTrue(AssetEdit.parse("이미지를 배경으로 깔아줘", REFS, page()).isEmpty());
        // 없는 번호 — 지어내 꽂지 않는다
        assertTrue(AssetEdit.parse("5번 이미지를 배경으로 깔아줘", REFS, page()).isEmpty());
        // 그 영역에 못 쓰는 역할 — 로고는 hero 전용이다
        //   ★ 혜택 **배경**은 이제 된다(항목_영역_배경 참고). 여기서는 아직 막힌 조합을 쓴다
        assertTrue(AssetEdit.parse("혜택 영역에 1번 이미지를 로고로 넣어줘", REFS, page()).isEmpty());
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

    /* ── 역할 단어가 없는 요청 ──────────────────────────────────── */

    // ★★ 제보: "왼쪽에 1번이미지를 추가해줘" 가 제목 위에 겹쳐 놓였다.
    //   역할 단어("그림")가 없어서 비켜섰고, 모델이 받아서 아무 자리에 꽂은 것이다.
    //   자리를 가리키는 말만 있어도 뜻은 또렷하다 — "제목 옆 그림" 으로 읽는다.
    @Test
    @DisplayName("★ 자리를 가리키는 말만 있어도 제목 옆 그림으로 읽는다")
    void 자리만_말해도() {
        AssetEdit.Plan left = AssetEdit.parse("왼쪽에 1번이미지를 추가해줘", REFS, page()).orElseThrow();
        assertEquals(Asset.ART, left.role());
        assertTrue(left.left());

        AssetEdit.Plan right = AssetEdit.parse("오른쪽에 2번 이미지 넣어줘", REFS, page()).orElseThrow();
        assertEquals(Asset.ART, right.role());
        assertFalse(right.left());
    }

    // ★★ 제보: "1번이미지 지워줘" 에 "hero 영역은 필수라 지울 수 없습니다" 가 나갔다.
    //   역할 단어가 없어 비켜섰고, 라우터가 DELETE 로 읽어 **영역을** 지우려 한 것이다.
    //   빼 달라는 요청은 자리를 안 말해도 된다 — 그 이미지를 페이지 어디서든 뺀다.
    @Test
    @DisplayName("★ 자리를 안 말해도 그 이미지를 페이지 전체에서 뺀다")
    void 자리를_안_말한_빼기() {
        String page = "<div class=\"ev-container\"><section data-block=\"hero\">"
                + "<img class=\"ev-asset ev-asset-bg\" src=\"" + U1 + "\">"
                + "<img class=\"ev-asset ev-asset-art\" src=\"" + U2 + "\">"
                + "<h1>봄맞이 축제</h1></section></div>";

        AssetEdit.Plan plan = AssetEdit.parse("1번이미지 지워줘", REFS, page()).orElseThrow();
        assertNull(plan.role(), "자리를 안 말했으면 role 이 없다");

        Document d = Jsoup.parseBodyFragment(AssetEdit.apply(page, plan, REFS));
        assertEquals(0, d.select("img[src=" + U1 + "]").size());
        assertEquals(1, d.select("img[src=" + U2 + "]").size(), "빼라고 안 한 건 남아야 합니다");
        assertEquals("봄맞이 축제", d.selectFirst("h1").text());
    }

    @Test
    @DisplayName("역할도 자리도 없으면 비켜선다")
    void 역할도_자리도_없으면() {
        assertTrue(AssetEdit.parse("1번 이미지를 추가해줘", REFS, page()).isEmpty());
        assertTrue(AssetEdit.parse("왼쪽 정렬로 바꿔줘", REFS, page()).isEmpty());
    }

    /* ── 막힌 조합 ──────────────────────────────────────────────── */

    // ★★ 제보: "1번 이미지를 혜택 배경으로" 했더니 혜택 카드 하나에 사진이 통째로 들어가
    //   카드가 늘어나고 옆칸이 텅 비었다. 그때는 막힌 조합으로 두고 거절했는데,
    //   **관리자가 하고 싶은 일이 맞았다** — 막을 게 아니라 되게 만드는 게 맞다.
    //   이제 카드에 반투명 판을 깔아서 글자가 읽힌다(95-assets/01-assets.css).
    @Test
    @DisplayName("★ 혜택 · 참여방법 영역에도 배경 사진이 들어간다")
    void 항목_영역_배경() {
        Document d = apply(page(), "1번 이미지를 혜택 배경으로 넣어줘");

        Element img = d.selectFirst("section[data-block=benefits] > img.ev-asset-bg");
        assertNotNull(img, "혜택 영역에 배경이 들어가야 합니다");
        // ★★ 카드 **안**이 아니라 섹션의 직계 자식이어야 한다. 카드 안에 들어가면
        //   그 카드만 늘어나고 CSS 규칙이 하나도 안 걸린다 — 처음 터진 사고가 그거였다
        assertEquals(0, d.select(".benefit-icon img.ev-asset-bg").size());
        assertEquals(0, d.select("li img.ev-asset-bg").size());
        // 문구는 그대로다
        assertTrue(d.html().contains("커피 쿠폰"));
    }

    // ★★ 아직 막혀 있는 조합은 모델에게 넘기지 않고 이유를 말한다.
    //   또렷하게 말한 요청을 비켜서면 모델이 받아서 어떻게든 끼워 넣는다.
    @Test
    @DisplayName("★ 막힌 조합은 모델에게 넘기지 않고 이유를 말한다")
    void 막힌_조합() {
        // 제목 옆 그림은 hero 전용이다 — 혜택에는 글자 칸을 나눌 구조가 없다
        AssetEdit.Result r = AssetEdit.read("1번 이미지를 혜택에 제목 옆 그림으로 넣어줘", REFS, page());

        assertInstanceOf(AssetEdit.Result.CannotDo.class, r);
        String message = ((AssetEdit.Result.CannotDo) r).message();
        assertTrue(message.contains("혜택"), message);
        // ★ 대신 할 수 있는 것을 같이 말한다 — 안 그러면 같은 말을 바꿔 가며 계속 시도한다
        assertTrue(message.contains("아이콘"), message);
    }

    /* ── 고정 번호 ──────────────────────────────────────────────── */

    // ★★ 제보: 1번을 쓰고 썸네일에서 지우고 다른 사진을 올리면 그게 1번이 됐다 —
    //   "1번 이미지를 배경으로" 가 **다른 사진**을 가리키게 되고 관리자는 그걸 모른다.
    //   번호를 목록 순서가 아니라 행(Asset.Ref.seq)에서 읽으면 그 일이 안 생긴다.
    private static final List<Asset.Ref> GAPPED = List.of(
            new Asset.Ref(1, U1, "벚꽃", 0, Asset.Tone.LIGHT),
            new Asset.Ref(3, U2, "로고", 0, Asset.Tone.DARK));

    @Test
    @DisplayName("★ 번호는 목록 순서가 아니라 고정 번호다 — 2번을 지워도 3번은 3번이다")
    void 고정_번호() {
        AssetEdit.Plan plan = AssetEdit.parse("3번 이미지를 배경으로 깔아줘", GAPPED, page()).orElseThrow();
        assertEquals(3, plan.seq());

        Document d = Jsoup.parseBodyFragment(AssetEdit.apply(page(), plan, GAPPED));
        // ★ 목록의 두 번째가 아니라 **3번** 의 주소가 들어가야 한다
        assertEquals(U2, d.selectFirst("img.ev-asset-bg").attr("src"));
    }

    // ★ 구멍이 생긴 번호를 말하면 **지금 있는 번호를 다 적어** 준다.
    //   "2장 있어요" 만으로는 1번과 3번 중 무엇을 불러야 할지 모른다
    @Test
    @DisplayName("★ 없는 번호를 말하면 지금 있는 번호를 알려 준다")
    void 구멍_난_번호() {
        AssetEdit.Result r = AssetEdit.read("2번 이미지를 배경으로 깔아줘", GAPPED, page());

        assertInstanceOf(AssetEdit.Result.CannotDo.class, r);
        String message = ((AssetEdit.Result.CannotDo) r).message();
        assertTrue(message.contains("1번"), message);
        assertTrue(message.contains("3번"), message);
    }

    // ★★ 서수는 **보이는 순서**다. 화면 목록의 두 번째가 3번 배지를 달고 있으면
    //   "두번째" 는 3번을 가리켜야 한다 — 관리자가 보는 것이 기준이다
    @Test
    @DisplayName("★ \"두번째\"는 보이는 순서다 — 번호에 구멍이 있어도 맞는 사진을 집는다")
    void 서수는_보이는_순서() {
        assertEquals(3, AssetEdit.parse("두번째 이미지를 배경으로 깔아줘", GAPPED, page()).orElseThrow().seq());
        assertEquals(1, AssetEdit.parse("첫번째 이미지를 배경으로 깔아줘", GAPPED, page()).orElseThrow().seq());
        // 목록보다 뒤를 말하면 없는 것으로 본다
        assertInstanceOf(AssetEdit.Result.CannotDo.class,
                AssetEdit.read("다섯번째 이미지를 배경으로 깔아줘", GAPPED, page()));
    }

    @Test
    @DisplayName("없는 번호도 이유를 말한다")
    void 없는_번호() {
        assertInstanceOf(AssetEdit.Result.CannotDo.class, AssetEdit.read("5번 이미지를 배경으로", REFS, page()));
        assertInstanceOf(AssetEdit.Result.CannotDo.class, AssetEdit.read("1번 이미지를 배경으로", List.of(), page()));
    }

    // ★ 영역을 안 말한 건 막힌 게 아니다 — 기본 자리로 보낸다
    @Test
    @DisplayName("영역을 안 말하면 막힌 게 아니다")
    void 영역을_안_말하면() {
        assertInstanceOf(AssetEdit.Result.Ok.class, AssetEdit.read("1번 이미지를 배경으로", REFS, page()));
        assertInstanceOf(AssetEdit.Result.NotMine.class, AssetEdit.read("혜택 배경색을 파랗게", REFS, page()));
    }

    // ★★ 제보와 같은 뿌리: 아이콘이 자리를 못 잡으면 CSS 규칙이 하나도 안 걸려
    //   원본 크기로 박힌다. 빈 자리로 옮기고, 자리가 없으면 지운다.
    @Test
    @DisplayName("★ 자리를 못 잡은 아이콘은 자리로 옮기거나 지운다")
    void 자리를_못_잡은_아이콘() {
        String outside = "<section data-block=\"benefits\"><ul><li>커피 쿠폰</li></ul>"
                + "<img class=\"ev-asset-icon\" src=\"" + U1 + "\"></section>";
        assertEquals(0, Jsoup.parseBodyFragment(BlockValidator.sanitizeGenerated(outside, REFS))
                .select("img").size());

        String withHolder = "<section data-block=\"benefits\"><ul>"
                + "<li><span class=\"benefit-icon\">🎁</span>커피</li></ul>"
                + "<img class=\"ev-asset-icon\" src=\"" + U1 + "\"></section>";
        Document d = Jsoup.parseBodyFragment(BlockValidator.sanitizeGenerated(withHolder, REFS));
        assertEquals(1, d.select(".benefit-icon img").size());
        assertEquals(0, d.select("section > img").size());
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
        assertTrue(AssetEdit.parse(null, REFS, page()).isEmpty());
        assertTrue(AssetEdit.parse("   ", REFS, page()).isEmpty());
        assertFalse(AssetEdit.parse("1번 이미지를 배경으로", List.of(), page()).isPresent());
    }

    @Test
    @DisplayName("없는 영역이면 페이지를 그대로 둔다")
    void 없는_영역() {
        String onlyCta = "<div class=\"ev-container\"><section data-block=\"cta\"><h2>참여</h2></section></div>";
        Optional<AssetEdit.Plan> plan = AssetEdit.parse("1번 이미지를 제목 옆 그림으로 넣어줘", REFS, page());

        assertEquals(onlyCta, AssetEdit.apply(onlyCta, plan.orElseThrow(), REFS));
    }

    /* ── 제보 v3~v12 에서 나온 것들 ─────────────────────────────── */

    private static final String WITH_PAGE_BG =
            "<div class=\"ev-container event-page\">"
            + "<img class=\"ev-asset ev-asset-page ev-asset-on-mixed\" src=\"" + U1 + "\">"
            + "<section data-block=\"hero\"><h1>봄맞이 축제</h1></section></div>";

    /**
     * ★★ 제보: "페이지 배경 없애줘" 를 세 번 말했는데 세 번 다 사진은 그대로였고,
     *   대신 제목 문구 · 테마 · 레이아웃이 바뀌었다. 문장에 "이미지" 라는 말이 없어서
     *   0회 경로가 비켜서고 모델이 받은 것이다 — 관리자는 세 번을 헛돌렸다.
     * ★ 자리 이름을 말했고 **그 자리에 사진이 실제로 깔려 있으면** 받는다.
     */
    @Test
    @DisplayName("★ \"이미지\" 라는 말이 없어도 자리 이름 + 사진이 있으면 뺀다")
    void 자리_이름만으로_뺀다() {
        for (String q : new String[] { "페이지 배경 없애줘", "페이지 전체 배경없애줘", "전체 배경 지워줘" }) {
            AssetEdit.Result r = AssetEdit.read(q, REFS, WITH_PAGE_BG);
            assertInstanceOf(AssetEdit.Result.Ok.class, r, q);
            assertEquals(Asset.PAGE, ((AssetEdit.Result.Ok) r).plan().role(), q);
        }
    }

    // ★★ 사진이 없으면 비켜선다. 그때 "배경 없애줘" 는 **배경색** 이야기일 수 있다 —
    //   페이지를 보고 정하니 지어내지 않는다
    @Test
    @DisplayName("★ 그 자리에 사진이 없으면 비켜선다")
    void 사진이_없으면_비켜선다() {
        assertInstanceOf(AssetEdit.Result.NotMine.class,
                AssetEdit.read("페이지 배경 없애줘", REFS, page()));
        assertInstanceOf(AssetEdit.Result.NotMine.class,
                AssetEdit.read("배경 지워줘", REFS, page()));
    }

    // ★ 색 이야기는 사진이 있어도 가로채지 않는다
    @Test
    @DisplayName("색 이야기는 가로채지 않는다")
    void 색_이야기() {
        assertInstanceOf(AssetEdit.Result.NotMine.class,
                AssetEdit.read("배경색을 초록색으로 바꿔줘", REFS, WITH_PAGE_BG));
        assertInstanceOf(AssetEdit.Result.NotMine.class,
                AssetEdit.read("배경색 지워줘", REFS, WITH_PAGE_BG));
    }

    /**
     * ★★ 제보: "문구 색을 어둡게 해줘" 가 v8 까지 가서도 흰색 그대로였다.
     *   모델은 "t-accent 로 강조함" 이라고 보고했고 화면은 안 바뀌었다 —
     *   사진 위 글자색은 CSS(95-assets)가 쥐고 있어서 class 로는 못 이긴다.
     */
    @Test
    @DisplayName("★ 사진 때문에 글자색이 잠겨 있으면 이유를 말한다")
    void 글자색이_잠김() {
        AssetEdit.Result r = AssetEdit.read("문구를 색을 어둡게 해줘", REFS, WITH_PAGE_BG);

        assertInstanceOf(AssetEdit.Result.CannotDo.class, r);
        String m = ((AssetEdit.Result.CannotDo) r).message();
        assertTrue(m.contains("배경 이미지 없애줘"), m);  // ★ 할 수 있는 길을 같이 준다
    }

    // ★ 밝은 그림 위에서는 테마 글자색이 살아 있다 — 잠긴 게 아니니 모델에게 보낸다
    @Test
    @DisplayName("밝은 그림 위에서는 글자색을 막지 않는다")
    void 밝은_그림은_안_막는다() {
        String light = "<div class=\"ev-container event-page\"><section data-block=\"hero\">"
                + "<img class=\"ev-asset ev-asset-bg ev-asset-on-light\" src=\"" + U1 + "\">"
                + "<h1>봄맞이 축제</h1></section></div>";
        assertInstanceOf(AssetEdit.Result.NotMine.class,
                AssetEdit.read("문구 색을 어둡게 해줘", REFS, light));
    }

    /**
     * ★★ 제보: "3번 이미지를 쿠폰 배경으로 깔아줘" 했더니 **hero 에** 들어갔다.
     *   영역 이름 표에 "쿠폰" 이 없어서 못 알아듣고 기본 자리로 보낸 것이다.
     */
    @Test
    @DisplayName("★ 쿠폰 · 경품 · 일정 같은 영역 이름도 알아듣는다")
    void 영역_이름() {
        assertEquals(Block.COUPON,
                AssetEdit.parse("1번 이미지를 쿠폰 배경으로 깔아줘", REFS, page()).orElseThrow().block());
        assertEquals(Block.PRIZE,
                AssetEdit.parse("1번 이미지를 경품 배경으로 깔아줘", REFS, page()).orElseThrow().block());
        assertEquals(Block.SCHEDULE,
                AssetEdit.parse("1번 이미지를 일정 배경으로 깔아줘", REFS, page()).orElseThrow().block());
        // ★ 긴 이름이 먼저 걸려야 한다 — "참여 버튼" 이 "참여 방법" 을 먹으면 안 된다
        assertEquals(Block.STEPS,
                AssetEdit.parse("1번 이미지를 참여 방법 배경으로 깔아줘", REFS, page()).orElseThrow().block());
        assertEquals(Block.CTA,
                AssetEdit.parse("1번 이미지를 참여 버튼 배경으로 깔아줘", REFS, page()).orElseThrow().block());
    }

    // ★ 유의사항만 막는다. 자기 크림색 판이 "읽고 넘어가라" 는 신호다
    @Test
    @DisplayName("유의사항 영역에는 배경을 막는다")
    void 유의사항은_막는다() {
        assertInstanceOf(AssetEdit.Result.CannotDo.class,
                AssetEdit.read("1번 이미지를 유의사항 배경으로 깔아줘", REFS, page()));
    }

    /* ── 번호를 안 말해도 되는 경우 ─────────────────────────────── */

    private static final List<Asset.Ref> ONE = List.of(
            new Asset.Ref(1, U1, "벚꽃", 0, Asset.Tone.LIGHT));

    /**
     * ★★ 제보: "이미지를 배경 전체로 깔아줘" 가 실패하고 hero 변형 목록만 떴다.
     *   올린 건 한 장뿐이었다 — 고를 게 없는데 "몇 번인지 안 말했다" 고 비켜선 것이다.
     *   관리자는 "1번" 을 붙여 다시 쳐야 했다. 한 장뿐이면 모호하지 않다.
     */
    @Test
    @DisplayName("★ 올린 게 한 장뿐이면 번호를 안 말해도 된다")
    void 한_장이면_번호가_필요없다() {
        for (String q : new String[] {
                "이미지를 배경 전체로 깔아줘", "이미지를 페이지 전체 배경으로 해줘",
                "이미지를 배경으로 깔아줘", "사진을 로고로 넣어줘" }) {
            AssetEdit.Result r = AssetEdit.read(q, ONE, page());
            assertInstanceOf(AssetEdit.Result.Ok.class, r, q);
            assertEquals(1, ((AssetEdit.Result.Ok) r).plan().seq(), q);
        }
    }

    /**
     * ★★ 제보: "배경 전체로 깔아줘" 가 **영역 배경**으로 떨어져서 hero 한 칸에만 깔렸다.
     *   표에 "페이지 전체 배경" 은 있고 "배경 전체" 는 없었다 — 말 순서만 다른 같은 뜻이다.
     */
    @Test
    @DisplayName("★ \"배경 전체\" 도 페이지 전체 배경으로 읽는다")
    void 말_순서를_안_가린다() {
        assertEquals(Asset.PAGE,
                AssetEdit.parse("1번 이미지를 배경 전체로 깔아줘", REFS, page()).orElseThrow().role());
        assertEquals(Asset.PAGE,
                AssetEdit.parse("1번 이미지를 전체 배경으로 깔아줘", REFS, page()).orElseThrow().role());
        // ★ "전체" 가 없으면 영역 배경 그대로다
        assertEquals(Asset.BG,
                AssetEdit.parse("1번 이미지를 배경으로 깔아줘", REFS, page()).orElseThrow().role());
    }

    /**
     * ★★ 제보: "이미지 없애줘" 에 "요청을 이해하지 못했습니다" 가 나왔다.
     *   번호도 자리도 안 말했지만 페이지에 사진이 한 장뿐이면 그게 그거다.
     */
    @Test
    @DisplayName("★ 페이지에 사진이 한 장뿐이면 번호 없이도 뺀다")
    void 한_장이면_번호없이_뺀다() {
        String withOne = "<div class=\"ev-container event-page\">"
                + "<img class=\"ev-asset ev-asset-page ev-asset-on-mixed\" src=\"" + U1 + "\">"
                + "<section data-block=\"hero\"><h1>봄맞이 축제</h1></section></div>";

        for (String q : new String[] { "이미지 없애줘", "사진 빼줘", "그림 지워줘" }) {
            AssetEdit.Result r = AssetEdit.read(q, REFS, withOne);
            assertInstanceOf(AssetEdit.Result.Ok.class, r, q);
            assertEquals(1, ((AssetEdit.Result.Ok) r).plan().seq(), q);
        }
    }

    /**
     * ★★ 여러 장이면 **지어내지 않고 되묻는다.** 모델에게 넘기면
     *   엉뚱한 걸 고르거나 "반영하지 못했습니다" 만 나온다 — 둘 다 관리자를 헛돌린다.
     */
    @Test
    @DisplayName("★ 여러 장이면 번호를 되묻는다 — 모델에게 넘기지 않는다")
    void 여러_장이면_되묻는다() {
        AssetEdit.Result put = AssetEdit.read("이미지를 배경으로 깔아줘", REFS, page());
        assertInstanceOf(AssetEdit.Result.CannotDo.class, put);
        assertTrue(((AssetEdit.Result.CannotDo) put).message().contains("1번"));
        assertTrue(((AssetEdit.Result.CannotDo) put).message().contains("2번"));

        String withTwo = "<div class=\"ev-container event-page\"><section data-block=\"hero\">"
                + "<img class=\"ev-asset ev-asset-bg\" src=\"" + U1 + "\">"
                + "<img class=\"ev-asset ev-asset-art\" src=\"" + U2 + "\">"
                + "<h1>봄맞이 축제</h1></section></div>";
        assertInstanceOf(AssetEdit.Result.CannotDo.class,
                AssetEdit.read("이미지 없애줘", REFS, withTwo));
    }

    // ★ 올린 게 없으면 올리라고 말한다. 되묻는 게 아니다
    @Test
    @DisplayName("올린 게 없으면 올리라고 말한다")
    void 올린_게_없으면() {
        AssetEdit.Result r = AssetEdit.read("이미지를 배경으로 깔아줘", List.of(), page());

        assertInstanceOf(AssetEdit.Result.CannotDo.class, r);
        assertTrue(((AssetEdit.Result.CannotDo) r).message().contains("+ 로 먼저 올려"));
    }

    /* ── 자리 이름을 헷갈려도 알아듣기 ─────────────────────────── */

    private static final String PAGE_BG_ONLY =
            "<div class=\"ev-container event-page\">"
            + "<img class=\"ev-asset ev-asset-page ev-asset-on-mixed\" src=\"" + U1 + "\">"
            + "<section data-block=\"hero\"><h1>봄맞이 축제</h1></section></div>";

    /**
     * ★★ 제보: 페이지 전체 배경을 깔아 놓고 "배경이미지 없애줘" 했더니
     *   "그 자리에는 이미지가 없어요" 가 나왔다. 사진은 루트에 있고(ev-asset-page)
     *   "배경" 은 영역 배경(ev-asset-bg)으로 읽혀서 hero 안만 뒤진 것이다.
     *   관리자가 둘을 구분해 부를 이유가 없다 — 배경이 하나뿐이면 그게 그거다.
     */
    @Test
    @DisplayName("★ \"배경\" 이 어느 쪽인지는 페이지가 안다")
    void 배경_두_종류를_바꿔_읽는다() {
        for (String q : new String[] { "이미지 배경에서 빼줘", "배경이미지 없애줘", "배경 이미지 지워줘" }) {
            AssetEdit.Result r = AssetEdit.read(q, REFS, PAGE_BG_ONLY);
            assertInstanceOf(AssetEdit.Result.Ok.class, r, q);
            assertEquals(Asset.PAGE, ((AssetEdit.Result.Ok) r).plan().role(), q);
        }
    }

    // ★★ 다른 자리로는 안 넘어간다. "로고 빼줘" 를 배경 빼기로 읽으면 안 된다 —
    //   로고라고 또렷하게 말한 요청이다. 대신 **어디에 있는지** 알려 준다
    @Test
    @DisplayName("★ 다른 자리로는 넘어가지 않고 어디 있는지 알려 준다")
    void 다른_자리로는_안_넘어간다() {
        AssetEdit.Result r = AssetEdit.read("로고 이미지 빼줘", REFS, PAGE_BG_ONLY);

        assertInstanceOf(AssetEdit.Result.CannotDo.class, r);
        String m = ((AssetEdit.Result.CannotDo) r).message();
        assertTrue(m.contains("로고"), m);
        assertTrue(m.contains("페이지 전체 배경"), m);   // ★ 있는 곳을 알려 준다
    }

    @Test
    @DisplayName("페이지에 이미지가 하나도 없으면 그렇다고 말한다")
    void 하나도_없으면() {
        AssetEdit.Result r = AssetEdit.read("배경 이미지 없애줘", REFS, page());

        assertInstanceOf(AssetEdit.Result.CannotDo.class, r);
        assertTrue(((AssetEdit.Result.CannotDo) r).message().contains("넣은 이미지가 없어요"));
    }

    /**
     * ★★ 버전 이력에 "hero ev-asset-bg" 가 찍혔다(실측) — 관리자가 CSS class 를 읽게 됐다.
     *   이름은 사람이 읽는 쪽으로 쓴다. 여기가 그 이름을 만드는 두 조각이다.
     */
    @Test
    @DisplayName("★ 자리 이름에 CSS class 가 새지 않는다")
    void 사람이_읽는_이름() {
        assertEquals("배경", Asset.BG.label());
        assertEquals("페이지 전체 배경", Asset.PAGE.label());
        for (Asset a : Asset.values()) {
            assertFalse(a.label().contains("ev-asset"), a.name());
        }
        // ★ "영역" 을 두 번 쓰지 않는다 — shortName 이 이미 "…영역" 으로 끝난다
        String where = AssetEdit.shortName(Block.HERO) + " " + Asset.BG.label();
        assertFalse(where.contains("영역 영역"), where);
    }

    /* ── 버전 이력 한 줄 ────────────────────────────────────────── */

    /**
     * ★★ 실측 사고: "1번 이미지 빼줘" 가 **NPE** 로 터졌다. 관리자에게는
     *   "페이지를 고치는 중 문제가 생겼습니다" 만 보였다 — 이미지는 안 빠졌고 이유도 못 들었다.
     *
     *   자리를 안 말하고 뺀 경우에는 role · block 이 **둘 다 null** 인데,
     *   자리 이름을 먼저 만들고 나서 role 의 null 을 보는 순서였다. block.desc() 에서 터진다.
     *   한 줄 순서가 바뀐 것뿐이라, 테스트가 없으면 다음에도 똑같이 난다.
     */
    @Test
    @DisplayName("★ 자리를 안 말하고 뺀 계획도 이력 문구를 만든다 — role · block 이 null 이다")
    void 이력_문구_null_안전() {
        AssetEdit.Plan bare = new AssetEdit.Plan(1, null, null, true, false);

        assertEquals("1번 이미지 뺌", AssetEdit.summary(bare));
    }

    @Test
    @DisplayName("★ 이력 문구에 CSS class 와 영역 key 가 새지 않는다")
    void 이력_문구에_내부이름이_없다() {
        for (Asset role : Asset.values()) {
            for (Block b : Block.values()) {
                if (!role.allowedOn(b)) continue;
                for (boolean remove : new boolean[] { true, false }) {
                    String out = AssetEdit.summary(new AssetEdit.Plan(1, role, b, remove, false));
                    assertFalse(out.contains("ev-asset"), out);
                    assertFalse(out.contains("data-block"), out);
                    assertFalse(out.contains("영역 영역"), out);
                }
            }
        }
    }

    @Test
    @DisplayName("이력 문구가 자리를 사람 말로 적는다")
    void 이력_문구_읽기() {
        assertEquals("이미지 넣음 — 페이지 전체 배경",
                AssetEdit.summary(new AssetEdit.Plan(1, Asset.PAGE, Block.HERO, false, false)));
        assertTrue(AssetEdit.summary(new AssetEdit.Plan(1, Asset.BG, Block.BENEFITS, false, false))
                .endsWith("배경"));
        assertTrue(AssetEdit.summary(new AssetEdit.Plan(1, Asset.BG, Block.HERO, true, false))
                .startsWith("이미지 뺌 — "));
    }

    /* ── "이미지" 라는 말 없이 까는 요청 ───────────────────────── */

    /**
     * ★★ 제보: "페이지 전체 배경으로 해줘" 가 실패하고 hero 변형 목록만 떴다.
     *   "이미지" 라는 말이 없어서 비켜섰다 — 제거만 받고 깔기는 안 받았다.
     *
     * ★ 페이지 전체 배경만 이렇게 받는다. 사진으로만 깔 수 있는 자리라서다
     *   (ev-asset-page 가 유일한 길). 영역 배경은 색 · 그라데이션 이야기일 때가 많아 못 받는다.
     */
    @Test
    @DisplayName("★ \"페이지 전체 배경으로 해줘\" — \"이미지\" 라는 말이 없어도 깐다")
    void 말_없이_페이지배경() {
        for (String q : new String[] {
                "페이지 전체 배경으로 해줘", "전체 배경으로 깔아줘",
                "배경 전체로 해줘", "페이지 뒤에 깔아줘" }) {
            AssetEdit.Result r = AssetEdit.read(q, ONE, page());
            assertInstanceOf(AssetEdit.Result.Ok.class, r, q);
            assertEquals(Asset.PAGE, ((AssetEdit.Result.Ok) r).plan().role(), q);
        }
    }

    /**
     * ★★ 여기가 이 기능의 **안전선**이다. 겉모양 · 색 요청을 사진 깔기로 읽으면
     *   관리자가 색을 바꿔 달라고 했는데 사진이 깔린다 — 되돌리기도 어렵다.
     *   색 이름까지 막는다. "연한 초록으로" 에는 "색" 이라는 글자가 없다.
     */
    @Test
    @DisplayName("★ 겉모양 · 색 요청은 가로채지 않는다")
    void 겉모양은_모델에게() {
        for (String q : new String[] {
                "페이지 배경을 연한 초록으로 해줘", "페이지 전체 배경을 그라데이션으로",
                "페이지를 투명하게 해줘", "전체 색감을 가을 느낌으로 바꿔줘",
                "배경색을 초록색으로 바꿔줘" }) {
            assertInstanceOf(AssetEdit.Result.NotMine.class, AssetEdit.read(q, ONE, page()), q);
        }
    }

    // ★ 영역 배경은 안 받는다 — "hero 배경을 밝게" 같은 말이 너무 흔하다
    @Test
    @DisplayName("영역 배경은 \"이미지\" 라는 말이 있어야 한다")
    void 영역배경은_말이_있어야() {
        assertInstanceOf(AssetEdit.Result.NotMine.class,
                AssetEdit.read("배경으로 해줘", ONE, page()));
        assertInstanceOf(AssetEdit.Result.Ok.class,
                AssetEdit.read("이미지를 배경으로 해줘", ONE, page()));
    }
}
