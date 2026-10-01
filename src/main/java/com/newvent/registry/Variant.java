package com.newvent.registry;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jsoup.nodes.Element;

/**
 * 블록 모양 변형 — 모델이 **고르기만** 하는 class 목록.
 *
 * ★ 왜 class 를 고르게 하나
 *   모델에게 CSS 나 인라인 style 을 쓰게 하면 디자인이 깨지고 검증할 수도 없다.
 *   사람이 event.css 에 만들어 둔 모양 중에서 이름 하나를 고르게 하면
 *   결과가 항상 검증된 디자인 안에 있고, 작은 모델도 정확히 해낸다.
 *
 * ★ 이 파일과 event.css 의 `.v-*` 규칙은 짝이다
 *   여기에 추가하면 event.css 에도 규칙을 추가한다. 한쪽만 있으면
 *   모델이 고른 class 가 아무 효과도 없거나(CSS 없음), 정화에서 지워진다(여기 없음).
 *
 * ★ 템플릿 블록(.ev-block)에는 효과가 없다
 *   event.css 의 `.v-*` 규칙은 전부 `[data-block]:not(.ev-block)` 에만 건다.
 *   템플릿은 자기 디자인(.hl-* · .sp-* …)이 있고, 섞으면 둘 다 망가진다.
 *   그래서 수정 프롬프트도 템플릿 블록에는 변형을 안내하지 않는다.
 *
 * ★ 같은 묶음(Group)에서는 하나만 남긴다 — {@link #sanitize}
 */
public enum Variant {

    // ── hero ──
    HERO_CENTER(Block.HERO, Group.LAYOUT, "v-hero-center", "가운데 정렬, 그라데이션 배경"),
    HERO_LEFT(Block.HERO, Group.LAYOUT, "v-hero-left", "왼쪽 정렬, 잡지 표지 느낌"),
    HERO_MINIMAL(Block.HERO, Group.LAYOUT, "v-hero-minimal", "밝은 배경, 큰 글씨, 장식 없음"),
    HERO_POSTER(Block.HERO, Group.LAYOUT, "v-hero-poster", "아주 큰 제목, 포스터처럼 강렬하게"),
    HERO_OUTLINE(Block.HERO, Group.LAYOUT, "v-hero-outline", "흰 배경에 브랜드색 테두리"),

    // ── highlight ──
    HIGHLIGHT_RIBBON(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-ribbon", "가로 띠 배너 (기본)"),
    HIGHLIGHT_BADGE(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-badge", "둥근 배지 안 큰 글씨"),
    HIGHLIGHT_QUOTE(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-quote", "큰 따옴표가 붙은 인용형"),
    HIGHLIGHT_GLOW(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-glow", "빛나는 테두리의 어두운 배너"),
    // 안내 상자(콜아웃) — 경고창 모양. 아이콘은 CSS 가 붙인다
    HIGHLIGHT_ALERT(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-alert", "주의 안내 상자 (노란색, 느낌표)"),
    HIGHLIGHT_INFO(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-info", "알림 안내 상자 (파란색, i 아이콘)"),
    HIGHLIGHT_SUCCESS(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-success", "완료·혜택 안내 상자 (초록색, 체크)"),

    // ── intro ──
    INTRO_PLAIN(Block.INTRO, Group.LAYOUT, "v-intro-plain", "평범한 본문 (기본)"),
    INTRO_QUOTE(Block.INTRO, Group.LAYOUT, "v-intro-quote", "왼쪽 굵은 선이 있는 인용형"),
    INTRO_CENTER(Block.INTRO, Group.LAYOUT, "v-intro-center", "가운데 정렬, 여백 넉넉하게"),
    INTRO_LEAD(Block.INTRO, Group.LAYOUT, "v-intro-lead", "첫 문단을 크게 강조"),

    // ── benefits ──
    BENEFITS_LIST(Block.BENEFITS, Group.LAYOUT, "v-benefits-list", "세로 목록 (기본)"),
    BENEFITS_GRID(Block.BENEFITS, Group.LAYOUT, "v-benefits-grid", "2열 카드"),
    BENEFITS_COUPON(Block.BENEFITS, Group.LAYOUT, "v-benefits-coupon", "점선 테두리 쿠폰"),
    BENEFITS_NUMBERED(Block.BENEFITS, Group.LAYOUT, "v-benefits-numbered", "동그라미 번호가 붙은 목록"),
    BENEFITS_SPOTLIGHT(Block.BENEFITS, Group.LAYOUT, "v-benefits-spotlight", "첫 혜택을 크게, 나머지는 작게"),
    BENEFITS_CAROUSEL(Block.BENEFITS, Group.LAYOUT, "v-benefits-carousel", "옆으로 넘기는 카드 (캐러셀)"),

    // ── compare ──
    COMPARE_STRIPED(Block.COMPARE, Group.LAYOUT, "v-compare-striped", "줄무늬 표 (기본)"),
    COMPARE_BORDERED(Block.COMPARE, Group.LAYOUT, "v-compare-bordered", "칸마다 테두리"),
    COMPARE_HIGHLIGHT(Block.COMPARE, Group.LAYOUT, "v-compare-highlight", "마지막 열을 추천으로 강조"),
    COMPARE_MINIMAL(Block.COMPARE, Group.LAYOUT, "v-compare-minimal", "가로 구분선만 있는 깔끔한 표"),

    // ── audience ──
    AUDIENCE_CHIPS(Block.AUDIENCE, Group.LAYOUT, "v-audience-chips", "둥근 태그 칩 (기본)"),
    AUDIENCE_CHECKS(Block.AUDIENCE, Group.LAYOUT, "v-audience-checks", "체크 표시 목록"),
    AUDIENCE_CARDS(Block.AUDIENCE, Group.LAYOUT, "v-audience-cards", "2열 카드"),

    // ── steps ──
    STEPS_NUMBERED(Block.STEPS, Group.LAYOUT, "v-steps-numbered", "동그라미 번호 목록"),
    STEPS_TIMELINE(Block.STEPS, Group.LAYOUT, "v-steps-timeline", "세로 타임라인"),
    STEPS_CARDS(Block.STEPS, Group.LAYOUT, "v-steps-cards", "가로로 나란한 카드"),
    STEPS_ARROWS(Block.STEPS, Group.LAYOUT, "v-steps-arrows", "화살표로 이어지는 흐름"),
    STEPS_CAROUSEL(Block.STEPS, Group.LAYOUT, "v-steps-carousel", "옆으로 넘기는 단계 카드 (캐러셀)"),

    // ── faq ──
    FAQ_PLAIN(Block.FAQ, Group.LAYOUT, "v-faq-plain", "구분선 목록 (기본)"),
    FAQ_CARDS(Block.FAQ, Group.LAYOUT, "v-faq-cards", "질문마다 카드"),
    FAQ_QA(Block.FAQ, Group.LAYOUT, "v-faq-qa", "Q · A 배지가 붙은 형식"),
    // ★ 아래 둘은 마크업이 다르다 — <dl> 이 아니라 <details>. 설명에 그대로 적어 모델에게 보인다
    FAQ_ACCORDION(Block.FAQ, Group.LAYOUT, "v-faq-accordion",
            "눌러서 펼치는 아코디언. 이걸 고르면 <div class=\"ev-accordion\"> 안에 "
            + "<details><summary>질문</summary><p>답</p></details> 를 질문마다 쓴다"),
    FAQ_BRUTAL(Block.FAQ, Group.LAYOUT, "v-faq-brutal",
            "굵은 검정 테두리 + 그림자 아코디언. 마크업은 v-faq-accordion 과 같다"),

    // ── cta ──
    CTA_CENTER(Block.CTA, Group.LAYOUT, "v-cta-center", "가운데 버튼 (기본)"),
    CTA_WIDE(Block.CTA, Group.LAYOUT, "v-cta-wide", "가로로 꽉 찬 버튼"),
    CTA_PILL(Block.CTA, Group.LAYOUT, "v-cta-pill", "알약 모양 버튼"),
    CTA_BANNER(Block.CTA, Group.LAYOUT, "v-cta-banner", "색 배너 상자 안의 버튼"),
    CTA_OUTLINE(Block.CTA, Group.LAYOUT, "v-cta-outline", "테두리만 있는 버튼"),

    // ── 배경 — 내용 블록 공통 ──
    SURFACE_CARD(null, Group.SURFACE, "v-surface-card", "흰 카드 배경 (기본)"),
    SURFACE_TINT(null, Group.SURFACE, "v-surface-tint", "연한 브랜드색 배경"),
    SURFACE_DARK(null, Group.SURFACE, "v-surface-dark", "어두운 배경, 흰 글씨"),
    SURFACE_PLAIN(null, Group.SURFACE, "v-surface-plain", "배경 없이 본문만");

    /** 같은 묶음에서는 하나만 쓴다 */
    public enum Group {
        /** 블록 전용 배치·모양 */
        LAYOUT,
        /** 블록 배경 — {@link #SURFACE_BLOCKS} 에만 */
        SURFACE
    }

    /** 변형 class 의 공통 접두사. 정화와 class 비교에서 이걸로 알아본다 */
    public static final String PREFIX = "v-";

    /**
     * 배경 변형을 쓸 수 있는 블록.
     * hero · highlight · cta 는 자기 배경이 곧 디자인이라 뺀다. notices 는 서버 소유다.
     */
    private static final Set<Block> SURFACE_BLOCKS =
            EnumSet.of(Block.INTRO, Block.BENEFITS, Block.COMPARE, Block.AUDIENCE, Block.STEPS, Block.FAQ);

    private final Block block;
    private final Group group;
    private final String cssClass;
    private final String desc;

    Variant(Block block, Group group, String cssClass, String desc) {
        this.block = block;
        this.group = group;
        this.cssClass = cssClass;
        this.desc = desc;
    }

    public Block block()     { return block; }
    public Group group()     { return group; }
    public String cssClass() { return cssClass; }
    public String desc()     { return desc; }

    /** 그 블록에 쓸 수 있는 변형 전부 — 전용 배치 먼저, 배경 나중 */
    public static List<Variant> of(Block b) {
        return Arrays.stream(values())
                .filter(v -> v.block == b || (v.group == Group.SURFACE && SURFACE_BLOCKS.contains(b)))
                .toList();
    }

    public static Optional<Variant> find(String cssClass) {
        if (cssClass == null) return Optional.empty();
        return Arrays.stream(values()).filter(v -> v.cssClass.equals(cssClass)).findFirst();
    }

    /** 변형 class 처럼 생겼나 — 목록에 있든 없든 */
    public static boolean looksLike(String cssClass) {
        return cssClass != null && cssClass.startsWith(PREFIX);
    }

    /**
     * 블록 루트의 변형 class 를 정리한다. **모델 출력에만 건다.**
     *
     * 지우는 것
     *   - 이 블록에 쓸 수 없는 v-* (목록에 없거나 다른 블록 것)
     *   - 같은 묶음의 두 번째부터 — 앞에 쓴 것을 남긴다
     *
     * ★ v-* 가 아닌 class 는 건드리지 않는다. 템플릿 class(.ev-block · .hl-hero …)가 있다
     */
    public static void sanitize(Element section, Block b) {
        List<Variant> allowed = of(b);
        EnumSet<Group> used = EnumSet.noneOf(Group.class);
        for (String c : List.copyOf(section.classNames())) {
            if (!looksLike(c)) continue;
            Optional<Variant> v = find(c).filter(allowed::contains);
            if (v.isEmpty() || !used.add(v.get().group)) {
                section.removeClass(c);
            }
        }
    }
}
