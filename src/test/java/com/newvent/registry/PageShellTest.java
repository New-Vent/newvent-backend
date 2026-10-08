package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 저장되는 조각이 혼자서도 성립하는가.
 *
 * ★ 이 테스트가 막으려는 실제 사고
 *   Bedrock 스모크에서 백지 생성 결과에 theme- 문자열이 0개였고,
 *   래퍼가 없어서 event.css 가 **하나도** 안 걸렸다.
 *   그리고 유의사항이 통째로 빠진 채로 검증을 통과했다 —
 *   validateGenerated 는 llmBlocks() 만 순회하므로 SERVER 블록 누락을 못 본다.
 */
class PageShellTest {

    /** 백지 생성이 실제로 낸 모양 (315자). 래퍼도 notices 도 없다. */
    private static final String 백지 = """
            <section data-block="hero">
              <h1>스모크 이벤트</h1>
              <p>시원한 여름, 데이터 걱정 없이 즐기세요!</p>
            <p data-slot="period"></p></section>
            <section data-block="benefits">
             <ul>
              <li>데이터 3GB 제공</li>
              <li>경품 추첨 기회 제공</li>
             </ul>
            </section>
            <section data-block="cta">
              <a href="#" class="btn">참여하기</a>
            </section>""";

    /** 템플릿이 실제로 내는 모양. 래퍼는 있고 테마와 notices 는 이미 있다. */
    private static final String 템플릿 = """
            <div class="ev-container event-page">
            <section class="ev-block block-hero" data-block="hero"><h1>응원</h1></section>
            <section class="ev-block block-notices" data-block="notices">\
            <ul class="notice-list"><li>원래 있던 문구</li></ul></section>
            <section class="ev-block block-cta" data-block="cta"><button>참여</button></section>
            </div>""";

    private static Element rootOf(String html) {
        return Jsoup.parseBodyFragment(html).body().selectFirst(".ev-container, .event-page");
    }

    /** hero 의 class 에 하나를 끼워 넣는다 — 모델이 고른 모양을 흉내 낸다 */
    private static String heroClass(String fragment, String cssClass) {
        return fragment.replace("<section data-block=\"hero\">",
                                "<section data-block=\"hero\" class=\"" + cssClass + "\">");
    }

    // ── 래퍼 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("① 백지 — 래퍼가 없으면 만들어 감싼다")
    void 백지에_래퍼를_심는다() {
        String out = PageShell.ensureRoot(백지, null);

        Element root = rootOf(out);
        assertNotNull(root, "래퍼가 없습니다. event.css 는 전부 래퍼의 자손 선택자라 "
                + "이게 없으면 스타일이 하나도 안 걸립니다.\n─── 출력 ───\n" + out);
        assertTrue(root.hasClass("ev-container"));
        assertTrue(root.hasClass("event-page"));

        // ★ 블록이 전부 래퍼 안으로 들어가야 한다. 하나라도 밖에 남으면 그 블록만 무스타일이다.
        for (Block b : Block.values()) {
            Element outside = Jsoup.parseBodyFragment(out).body().selectFirst(b.selector());
            if (outside == null) continue;
            assertNotNull(outside.closest(".ev-container"),
                    b.key() + " 블록이 래퍼 밖에 남았습니다.");
        }
    }

    // ── 테마 ──────────────────────────────────────────────────────

    /**
     * ★ 동작이 <b>뒤집혔다.</b> 예전에는 백지에 테마를 붙이지 않았다 —
     *   그때는 백지용 테마가 없어서 붙일 게 없었고 :root 기본값에 맡길 수밖에 없었다.
     *   지금은 {@link Theme} 에 BASIC · BLOOM · AURORA 가 있다.
     *
     * ★ 테마가 없으면 event.css 의 :root 기본값만 걸린다 —
     *   같은 흰 카드가 세로로 쌓인 화면이다. 그래서 반드시 하나 박는다.
     *
     * ★ 기본값은 성격이 없어야 한다. BLOOM 처럼 센 테마를 기본으로 두면
     *   "법인 요금제 비교" 에 구름이 뜬다. 조용한 기본값은 조금 안 맞아도 괜찮지만
     *   시끄러운 기본값은 그냥 틀린다.
     */
    @Test
    @DisplayName("② 백지에도 테마를 붙인다 — 모델이 안 고르면 기본 테마")
    void 백지에_기본_테마를_붙인다() {
        Element root = rootOf(PageShell.ensureRoot(백지, null));

        assertTrue(root.hasClass(Theme.DEFAULT.cssClass()),
                "백지에 기본 테마가 안 붙었습니다. 테마가 없으면 :root 기본값만 걸려 "
                + "같은 흰 카드가 세로로 쌓입니다. 실제 class: " + root.classNames());
    }

    /**
     * ★ 모델은 블록 하나만 출력한다. 루트를 못 만지므로 hero 의 class 에 고르게 하고
     *   서버가 루트로 옮긴다 — 팔레트와 같은 길이다(PageShell.hoistPalette).
     * ★ 올린 뒤에는 hero 에서 지운다. 남겨 두면 다음 수정에서 모델이 보고 따라 쓴다
     *   (v-* 에서 겪은 그 경로다).
     */
    @Test
    @DisplayName("②-b 백지 — 모델이 hero 에 고른 테마를 루트로 올린다")
    void 모델이_고른_테마를_루트로_올린다() {
        String out = PageShell.ensureRoot(heroClass(백지, "theme-bloom"), null);

        Element root = rootOf(out);
        assertTrue(root.hasClass("theme-bloom"),
                "모델이 고른 테마가 루트로 안 올라왔습니다. 실제 class: " + root.classNames());
        assertFalse(root.hasClass(Theme.DEFAULT.cssClass()),
                "모델이 골랐는데 기본 테마까지 같이 박혔습니다. 테마는 하나여야 합니다. "
                + "실제 class: " + root.classNames());

        Element hero = Jsoup.parseBodyFragment(out).body().selectFirst(Block.HERO.selector());
        assertFalse(hero.hasClass("theme-bloom"),
                "hero 에 테마가 남았습니다. 루트로 올렸으면 원래 자리에서는 지워야 합니다.");
    }

    /**
     * ★ 지어낸 이름(theme-두쫀쿠)은 CSS 가 없어 효과도 없지만, 남겨 두면
     *   다음 수정에서 모델이 그걸 보고 따라 쓴다. {@code BlockValidator.cleanLooks} 가
     *   들어오는 자리에서 지우고, 지운 뒤에는 기본 테마로 떨어져야 한다.
     */
    @Test
    @DisplayName("②-c 백지 — 모델이 지어낸 테마는 지우고 기본 테마로 떨어진다")
    void 지어낸_테마는_기본으로_떨어진다() {
        String 정화됨 = BlockValidator.sanitizeGenerated(heroClass(백지, "theme-두쫀쿠"));
        assertFalse(정화됨.contains("theme-두쫀쿠"),
                "지어낸 테마가 안 지워졌습니다.\n─── 출력 ───\n" + 정화됨);

        Element root = rootOf(PageShell.ensureRoot(정화됨, null));
        assertTrue(root.hasClass(Theme.DEFAULT.cssClass()),
                "지어낸 테마를 지운 뒤 기본 테마가 안 박혔습니다. 실제 class: " + root.classNames());
    }

    /**
     * ★★ 이 테스트가 막는 사고 — 실제로 났다(SavedTemplateHtmlTest 5건).
     *   {@code cleanLooks} 는 class 가 있는 <b>모든 요소</b>를 돈다. 루트는 section 이 아니라
     *   "hero 가 아니다" 로 판정되어 theme-sports 가 지워졌고, 그다음 ensureRoot 가
     *   "테마 없음" 으로 보고 기본 테마를 박아 <b>템플릿 테마가 전부 basic 이 됐다.</b>
     *   루트의 테마는 서버가 쥔다. 정화는 모델 출력만 다룬다.
     */
    @Test
    @DisplayName("②-d ★ 정화를 지나도 루트의 템플릿 테마는 살아남는다")
    void 루트_테마는_정화에서_살아남는다() {
        String 테마템플릿 = 템플릿.replace("\"ev-container event-page\"",
                                        "\"ev-container event-page theme-sports\"");
        String 정화됨 = BlockValidator.sanitizeEdited(테마템플릿);

        Element root = rootOf(PageShell.ensureRoot(정화됨, null));
        assertTrue(root.hasClass("theme-sports"),
                "루트의 템플릿 테마가 지워졌습니다. 템플릿 5종이 전부 기본 테마가 됩니다. "
                + "실제 class: " + root.classNames());
        assertFalse(root.hasClass(Theme.DEFAULT.cssClass()),
                "이미 테마가 있는데 기본 테마까지 박혔습니다. 실제 class: " + root.classNames());
    }

    @Test
    @DisplayName("③ 템플릿 — 래퍼는 그대로 두고 테마 클래스만 보강한다")
    void 템플릿에_테마를_보강한다() {
        String out = PageShell.ensureRoot(템플릿, "sports_cheer");

        Element root = rootOf(out);
        assertTrue(root.hasClass("theme-sports"),
                "테마가 안 붙었습니다. 실제 class: " + root.classNames());

        // ★ 래퍼를 새로 만들어 이중으로 감싸면 안 된다.
        assertEquals(1, Jsoup.parseBodyFragment(out).body().select(".ev-container").size(),
                "래퍼가 두 겹이 됐습니다.");
    }

    @Test
    @DisplayName("④ ★ 두 번 걸어도 같다")
    void 멱등이다() {
        // ★ 생성과 수정이 같은 조각을 여러 번 지나간다. 한 번 더 걸릴 때마다
        //   래퍼가 겹치거나 유의사항이 두 개가 되면 조용히 망가진다.
        String 한번 = PageShell.plant(백지, "sports_cheer");
        String 두번 = PageShell.plant(한번, "sports_cheer");

        assertEquals(한번, 두번, "두 번 심었습니다.");
    }

    /**
     * ★ 기본 테마가 박힌 뒤에 다시 걸어도 테마가 둘이 되면 안 된다.
     *   ④ 의 멱등성을 테마 쪽에서만 따로 본다 — 실패했을 때 어디가 깨졌는지 바로 보이게.
     */
    @Test
    @DisplayName("④-b 두 번 걸어도 테마는 하나다")
    void 테마는_하나다() {
        String 두번 = PageShell.plant(PageShell.plant(백지, null), null);

        long n = rootOf(두번).classNames().stream().filter(Theme::looksLike).count();
        assertEquals(1, n, "루트의 테마가 " + n + "개입니다. 실제 class: " + rootOf(두번).classNames());
    }

    // ── 유의사항 ──────────────────────────────────────────────────

    @Test
    @DisplayName("⑤ 백지 — 유의사항을 서버가 넣는다")
    void 백지에_유의사항을_넣는다() {
        String out = PageShell.plant(백지, null);

        Document doc = Jsoup.parseBodyFragment(out);
        Element notices = doc.body().selectFirst(Block.NOTICES.selector());
        assertNotNull(notices, "유의사항이 없습니다. 이대로 게시되면 제세공과금·부정참여 "
                + "안내가 빠진 채 나갑니다.\n─── 출력 ───\n" + out);
        assertFalse(notices.select("li").isEmpty(), "유의사항 항목이 비어 있습니다.");
    }

    @Test
    @DisplayName("⑥ 유의사항은 Block 순서대로 cta 앞에 들어간다")
    void 유의사항_자리가_맞다() {
        Document doc = Jsoup.parseBodyFragment(PageShell.plant(백지, null));

        Element notices = doc.body().selectFirst(Block.NOTICES.selector());
        Element cta = doc.body().selectFirst(Block.CTA.selector());

        assertNotNull(notices);
        assertNotNull(cta);
        assertTrue(notices.siblingIndex() < cta.siblingIndex(),
                "유의사항이 참여 버튼 뒤에 붙었습니다. Block 순서는 NOTICES → CTA 입니다.");
    }

    @Test
    @DisplayName("⑦ 템플릿의 유의사항은 덮어쓰지 않는다")
    void 이미_있으면_두지_않는다() {
        String out = PageShell.plant(템플릿, "sports_cheer");

        Document doc = Jsoup.parseBodyFragment(out);
        assertEquals(1, doc.body().select(Block.NOTICES.selector()).size(),
                "유의사항이 두 개가 됐습니다.");
        assertTrue(out.contains("원래 있던 문구"),
                "템플릿의 유의사항이 덮어써졌습니다. 템플릿마다 문구가 다릅니다.");
    }

    // ── 공백 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("⑧ ★ 블록 안쪽 공백을 건드리지 않는다")
    void 재정렬하지_않는다() {
        // ★ 여기서 다시 들여쓰기를 하면 수정 한 번에 버전 diff 가 통째로 번진다.
        //   BlockMerge 가 애써 원본 공백을 지키는 이유가 사라진다.
        String out = PageShell.plant(백지, null);

        assertTrue(out.contains("  <h1>스모크 이벤트</h1>"),
                "hero 안쪽 들여쓰기가 바뀌었습니다 — pretty-print 가 켜져 있습니다.\n"
                + "─── 출력 ───\n" + out);
    }
}
