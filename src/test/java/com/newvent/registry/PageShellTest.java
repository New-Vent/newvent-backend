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

    @Test
    @DisplayName("② 백지에는 테마를 붙이지 않는다 — :root 기본값을 쓴다")
    void 백지는_테마가_없다() {
        Element root = rootOf(PageShell.ensureRoot(백지, null));

        assertTrue(root.classNames().stream().noneMatch(c -> c.startsWith("theme-")),
                "백지는 템플릿이 없으므로 테마를 고를 근거가 없습니다. "
                + "event.css 의 :root 기본값이 쓰입니다. 실제 class: " + root.classNames());
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
