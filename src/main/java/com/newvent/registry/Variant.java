package com.newvent.registry;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
    // 이식: Bootstrap jumbotron · Flowbite hero · DaisyUI hero
    HERO_SPLIT(Block.HERO, Group.LAYOUT, "v-hero-split", "왼쪽 제목 · 오른쪽 첫 문단을 큰 강조 상자로"),
    HERO_TICKET(Block.HERO, Group.LAYOUT, "v-hero-ticket", "양옆이 파인 티켓 모양"),
    HERO_GRADIENT_TEXT(Block.HERO, Group.LAYOUT, "v-hero-gradient-text", "밝은 배경 + 그라데이션 글씨 제목"),
    HERO_GLASS(Block.HERO, Group.LAYOUT, "v-hero-glass", "그라데이션 위 반투명 유리 카드"),
    HERO_WAVE(Block.HERO, Group.LAYOUT, "v-hero-wave", "아래가 물결 모양으로 끝나는 배너"),

    // ── highlight ──
    HIGHLIGHT_RIBBON(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-ribbon", "가로 띠 배너 (기본)"),
    HIGHLIGHT_BADGE(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-badge", "둥근 배지 안 큰 글씨"),
    HIGHLIGHT_QUOTE(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-quote", "큰 따옴표가 붙은 인용형"),
    HIGHLIGHT_GLOW(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-glow", "빛나는 테두리의 어두운 배너"),
    // 안내 상자(콜아웃) — 경고창 모양. 아이콘은 CSS 가 붙인다
    HIGHLIGHT_ALERT(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-alert", "주의 안내 상자 (노란색, 느낌표)"),
    HIGHLIGHT_INFO(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-info", "알림 안내 상자 (파란색, i 아이콘)"),
    HIGHLIGHT_SUCCESS(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-success", "완료·혜택 안내 상자 (초록색, 체크)"),
    // 이식: Bootstrap alert-danger · alert-dark · DaisyUI marquee
    HIGHLIGHT_DANGER(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-danger", "경고 안내 상자 (빨간색, 마감 · 주의)"),
    HIGHLIGHT_DARK(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-dark", "검정 안내 상자"),
    HIGHLIGHT_MARQUEE(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-marquee", "옆으로 흐르는 띠 (첫 문장만, 움직임 줄이기 설정이면 멈춤)"),
    HIGHLIGHT_STAMP(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-stamp", "기울어진 도장 모양"),
    HIGHLIGHT_NEON(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-neon", "어두운 바탕의 네온 글씨"),

    // ── intro ──
    INTRO_PLAIN(Block.INTRO, Group.LAYOUT, "v-intro-plain", "평범한 본문 (기본)"),
    INTRO_QUOTE(Block.INTRO, Group.LAYOUT, "v-intro-quote", "왼쪽 굵은 선이 있는 인용형"),
    INTRO_CENTER(Block.INTRO, Group.LAYOUT, "v-intro-center", "가운데 정렬, 여백 넉넉하게"),
    INTRO_LEAD(Block.INTRO, Group.LAYOUT, "v-intro-lead", "첫 문단을 크게 강조"),
    INTRO_DROPCAP(Block.INTRO, Group.LAYOUT, "v-intro-dropcap", "첫 글자를 크게 (잡지 본문)"),
    INTRO_TWO_COL(Block.INTRO, Group.LAYOUT, "v-intro-two-col", "문단을 2단으로"),
    INTRO_BOXED(Block.INTRO, Group.LAYOUT, "v-intro-boxed", "머리띠가 있는 카드 상자 (Bootstrap card)"),
    INTRO_SIDEBAR(Block.INTRO, Group.LAYOUT, "v-intro-sidebar", "왼쪽 소제목 · 오른쪽 본문"),

    // ── benefits ──
    BENEFITS_LIST(Block.BENEFITS, Group.LAYOUT, "v-benefits-list", "세로 목록 (기본)"),
    BENEFITS_GRID(Block.BENEFITS, Group.LAYOUT, "v-benefits-grid", "2열 카드"),
    BENEFITS_COUPON(Block.BENEFITS, Group.LAYOUT, "v-benefits-coupon", "점선 테두리 쿠폰"),
    BENEFITS_NUMBERED(Block.BENEFITS, Group.LAYOUT, "v-benefits-numbered", "동그라미 번호가 붙은 목록"),
    BENEFITS_SPOTLIGHT(Block.BENEFITS, Group.LAYOUT, "v-benefits-spotlight", "첫 혜택을 크게, 나머지는 작게"),
    BENEFITS_CAROUSEL(Block.BENEFITS, Group.LAYOUT, "v-benefits-carousel", "옆으로 넘기는 카드 (캐러셀)"),
    // 이식: Bootstrap pricing · Preline card · HyperUI ticket
    BENEFITS_TICKET(Block.BENEFITS, Group.LAYOUT, "v-benefits-ticket", "왼쪽 띠가 있는 티켓 카드"),
    BENEFITS_ICON(Block.BENEFITS, Group.LAYOUT, "v-benefits-icon", "항목 앞 원형 별 아이콘"),
    BENEFITS_PRICING(Block.BENEFITS, Group.LAYOUT, "v-benefits-pricing", "요금표처럼 큰 카드 (가운데 강조)"),
    BENEFITS_CHECKLIST(Block.BENEFITS, Group.LAYOUT, "v-benefits-checklist", "체크 표시 목록"),
    BENEFITS_STRIPE(Block.BENEFITS, Group.LAYOUT, "v-benefits-stripe", "한 줄씩 색이 번갈아 바뀌는 목록"),
    BENEFITS_THREE_COL(Block.BENEFITS, Group.LAYOUT, "v-benefits-three-col", "3열 카드"),

    // ── compare ──
    COMPARE_STRIPED(Block.COMPARE, Group.LAYOUT, "v-compare-striped", "줄무늬 표 (기본)"),
    COMPARE_BORDERED(Block.COMPARE, Group.LAYOUT, "v-compare-bordered", "칸마다 테두리"),
    COMPARE_HIGHLIGHT(Block.COMPARE, Group.LAYOUT, "v-compare-highlight", "마지막 열을 추천으로 강조"),
    COMPARE_MINIMAL(Block.COMPARE, Group.LAYOUT, "v-compare-minimal", "가로 구분선만 있는 깔끔한 표"),
    COMPARE_DARK(Block.COMPARE, Group.LAYOUT, "v-compare-dark", "어두운 표 (Bootstrap table-dark)"),
    COMPARE_COMPACT(Block.COMPARE, Group.LAYOUT, "v-compare-compact", "촘촘한 작은 표 (Bootstrap table-sm)"),
    COMPARE_ROUNDED(Block.COMPARE, Group.LAYOUT, "v-compare-rounded", "그림자 있는 둥근 카드형 표"),

    // ── audience ──
    AUDIENCE_CHIPS(Block.AUDIENCE, Group.LAYOUT, "v-audience-chips", "둥근 태그 칩 (기본)"),
    AUDIENCE_CHECKS(Block.AUDIENCE, Group.LAYOUT, "v-audience-checks", "체크 표시 목록"),
    AUDIENCE_CARDS(Block.AUDIENCE, Group.LAYOUT, "v-audience-cards", "2열 카드"),
    AUDIENCE_OUTLINE(Block.AUDIENCE, Group.LAYOUT, "v-audience-outline", "테두리만 있는 알약"),
    AUDIENCE_ICONS(Block.AUDIENCE, Group.LAYOUT, "v-audience-icons", "사람 아이콘이 붙은 목록"),
    AUDIENCE_INLINE(Block.AUDIENCE, Group.LAYOUT, "v-audience-inline", "가운뎃점으로 이어진 한 줄"),

    // ── steps ──
    STEPS_NUMBERED(Block.STEPS, Group.LAYOUT, "v-steps-numbered", "동그라미 번호 목록"),
    STEPS_TIMELINE(Block.STEPS, Group.LAYOUT, "v-steps-timeline", "세로 타임라인"),
    STEPS_CARDS(Block.STEPS, Group.LAYOUT, "v-steps-cards", "가로로 나란한 카드"),
    STEPS_ARROWS(Block.STEPS, Group.LAYOUT, "v-steps-arrows", "화살표로 이어지는 흐름"),
    STEPS_CAROUSEL(Block.STEPS, Group.LAYOUT, "v-steps-carousel", "옆으로 넘기는 단계 카드 (캐러셀)"),
    // 이식: DaisyUI steps · Flowbite stepper
    STEPS_PROGRESS(Block.STEPS, Group.LAYOUT, "v-steps-progress", "가로 진행 막대 위 단계 점"),
    STEPS_CHECKLIST(Block.STEPS, Group.LAYOUT, "v-steps-checklist", "체크 상자 목록"),
    STEPS_ZIGZAG(Block.STEPS, Group.LAYOUT, "v-steps-zigzag", "좌우로 번갈아 놓인 카드"),
    STEPS_BUBBLE(Block.STEPS, Group.LAYOUT, "v-steps-bubble", "말풍선 단계"),
    STEPS_MINIMAL(Block.STEPS, Group.LAYOUT, "v-steps-minimal", "큰 번호만 있는 깔끔한 목록"),

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
    FAQ_CHAT(Block.FAQ, Group.LAYOUT, "v-faq-chat", "말풍선 대화형 (목록형 dl)"),
    FAQ_FLUSH(Block.FAQ, Group.LAYOUT, "v-faq-flush",
            "구분선만 있는 아코디언 (Bootstrap accordion-flush). 마크업은 v-faq-accordion 과 같다"),
    FAQ_LEFTBAR(Block.FAQ, Group.LAYOUT, "v-faq-leftbar", "질문 왼쪽에 색 막대"),
    FAQ_NUMBERED(Block.FAQ, Group.LAYOUT, "v-faq-numbered", "질문에 번호"),

    // ── cta ──
    CTA_CENTER(Block.CTA, Group.LAYOUT, "v-cta-center", "가운데 버튼 (기본)"),
    CTA_WIDE(Block.CTA, Group.LAYOUT, "v-cta-wide", "가로로 꽉 찬 버튼"),
    CTA_PILL(Block.CTA, Group.LAYOUT, "v-cta-pill", "알약 모양 버튼"),
    CTA_BANNER(Block.CTA, Group.LAYOUT, "v-cta-banner", "색 배너 상자 안의 버튼"),
    CTA_OUTLINE(Block.CTA, Group.LAYOUT, "v-cta-outline", "테두리만 있는 버튼"),
    // 이식: Bootstrap btn-lg · HyperUI 버튼 · Flowbite gradient outline
    CTA_STICKY(Block.CTA, Group.LAYOUT, "v-cta-sticky", "화면 아래에 붙어 따라오는 버튼"),
    CTA_GLOW(Block.CTA, Group.LAYOUT, "v-cta-glow", "빛이 번지는 버튼"),
    CTA_GRADIENT_BORDER(Block.CTA, Group.LAYOUT, "v-cta-gradient-border", "그라데이션 테두리 버튼"),
    CTA_RAISED(Block.CTA, Group.LAYOUT, "v-cta-raised", "누르면 들어가는 입체 버튼"),
    CTA_ARROW(Block.CTA, Group.LAYOUT, "v-cta-arrow", "오른쪽 화살표가 붙은 버튼"),
    CTA_DARK(Block.CTA, Group.LAYOUT, "v-cta-dark", "어두운 상자 안의 밝은 버튼"),

    // ── stats ──
    STATS_GRID(Block.STATS, Group.LAYOUT, "v-stats-grid", "2열 큰 숫자 카드 (기본)"),
    STATS_INLINE(Block.STATS, Group.LAYOUT, "v-stats-inline", "한 줄에 나란히"),
    STATS_RING(Block.STATS, Group.LAYOUT, "v-stats-ring", "원형 테두리 안 숫자"),
    STATS_BAR(Block.STATS, Group.LAYOUT, "v-stats-bar", "숫자 아래 강조 막대"),

    // ── prize ──
    PRIZE_LIST(Block.PRIZE, Group.LAYOUT, "v-prize-list", "등수 목록 (기본)"),
    PRIZE_PODIUM(Block.PRIZE, Group.LAYOUT, "v-prize-podium", "1 · 2 · 3등 단상 (앞의 세 항목)"),
    PRIZE_CARDS(Block.PRIZE, Group.LAYOUT, "v-prize-cards", "경품 카드"),
    PRIZE_RIBBON(Block.PRIZE, Group.LAYOUT, "v-prize-ribbon", "첫 항목에 리본을 단 1등 강조"),

    // ── coupon ──
    COUPON_TICKET(Block.COUPON, Group.LAYOUT, "v-coupon-ticket", "양옆이 파인 쿠폰 티켓 (기본)"),
    COUPON_DASHED(Block.COUPON, Group.LAYOUT, "v-coupon-dashed", "점선 쿠폰 상자"),
    COUPON_STAMP(Block.COUPON, Group.LAYOUT, "v-coupon-stamp", "도장이 찍힌 쿠폰"),
    COUPON_CARD(Block.COUPON, Group.LAYOUT, "v-coupon-card", "어두운 카드형 쿠폰"),

    // ── schedule ──
    SCHEDULE_TIMELINE(Block.SCHEDULE, Group.LAYOUT, "v-schedule-timeline", "세로 타임라인 (기본)"),
    SCHEDULE_CHIPS(Block.SCHEDULE, Group.LAYOUT, "v-schedule-chips", "날짜 칩"),
    SCHEDULE_TABLE(Block.SCHEDULE, Group.LAYOUT, "v-schedule-table", "표처럼 줄 맞춤"),
    SCHEDULE_CARDS(Block.SCHEDULE, Group.LAYOUT, "v-schedule-cards", "가로 카드"),

    // ── 배경 — 내용 블록 공통 ──
    SURFACE_CARD(null, Group.SURFACE, "v-surface-card", "흰 카드 배경 (기본)"),
    SURFACE_TINT(null, Group.SURFACE, "v-surface-tint", "연한 브랜드색 배경"),
    SURFACE_DARK(null, Group.SURFACE, "v-surface-dark", "어두운 배경, 흰 글씨"),
    SURFACE_PLAIN(null, Group.SURFACE, "v-surface-plain", "배경 없이 본문만"),
    SURFACE_GRADIENT(null, Group.SURFACE, "v-surface-gradient", "연한 그라데이션 배경"),
    SURFACE_GLASS(null, Group.SURFACE, "v-surface-glass", "반투명 유리"),
    SURFACE_DOTS(null, Group.SURFACE, "v-surface-dots", "점무늬 배경"),
    SURFACE_STRIPES(null, Group.SURFACE, "v-surface-stripes", "사선 무늬 배경"),
    SURFACE_OUTLINE(null, Group.SURFACE, "v-surface-outline", "브랜드색 테두리만"),
    SURFACE_SHADOW(null, Group.SURFACE, "v-surface-shadow", "떠 있는 그림자 카드");

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
     * hero · highlight · coupon · cta 는 자기 배경이 곧 디자인이라 뺀다. notices 는 서버 소유다.
     */
    private static final Set<Block> SURFACE_BLOCKS =
            // ★ coupon 은 넣지 않는다 — 쿠폰은 그라데이션 바탕 + 흰 글씨가 곧 디자인이다.
            //   배경 변형이 붙으면 바탕만 흰색으로 덮이고 글씨는 흰색으로 남아 빈 상자가 된다(Bedrock 실측).
            EnumSet.of(Block.INTRO, Block.STATS, Block.BENEFITS, Block.PRIZE, Block.COMPARE,
                    Block.AUDIENCE, Block.STEPS, Block.SCHEDULE, Block.FAQ);

    /**
     * 항목이 몇 개 이상이어야 말이 되는 변형. <b>프롬프트가 아니라 서버가 지킨다.</b>
     *
     * ★ 왜 프롬프트로 안 하나 — 해 봤고, 정반대로 터졌다.
     *   "항목이 3개 미만이면 캐러셀(v-benefits-carousel · v-steps-carousel)을 고르지 마라" 를
     *   넣었더니, 그 줄이 프롬프트에서 <b>v-benefits-carousel 이라는 이름이 나오는 유일한 자리</b>가
     *   됐다(표본에는 pricing · checklist · stripe · three-col 만 실렸다).
     *   모델은 금지문에서 이름을 배워서 혜택 2개짜리에 캐러셀을 골랐다(여름 수영장 2차 실측).
     *   <b>금지하려고 쓴 이름이 추천이 됐다.</b>
     *
     * ★ 그래서 말하지 않고 지운다. 모델이 골라도 여기서 떨어진다.
     *   event.css 가 li { flex: 0 0 78% } 라서 2개면 둘째 카드가 잘린 채 멈춘다.
     *   시상대(v-prize-podium)도 1 · 2 · 3등 자리를 CSS 가 정해 두어 3개 미만이면 빈다.
     */
    private static final Map<Variant, Integer> NEEDS_ITEMS = Map.of(
            BENEFITS_CAROUSEL, 3,
            STEPS_CAROUSEL, 3,
            PRIZE_PODIUM, 3);

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

    /**
     * 생성 프롬프트에 실을 배치 변형 — 기본(첫 번째)을 뺀 나머지에서 요청문으로 고른 max 개.
     *
     * ★ 왜 전부 안 싣나 — 블록이 14종 · 변형이 100개를 넘으면 생성 입력이 두 배가 되고,
     *   작은 모델은 목록이 길수록 앞쪽 몇 개만 고른다. 블록마다 몇 개만 보여주는 편이 낫다.
     * ★ 결정적이다 — 같은 seed(요청문)면 같은 목록. 재시도 사이에 선택지가 바뀌지 않는다.
     *   seed 가 다르면 시작 위치가 달라져 페이지마다 다른 변형이 보인다.
     * ★ 수정 프롬프트는 이걸 쓰지 않는다 — 블록 하나라서 전부 보여도 짧다.
     */
    public static List<Variant> sample(Block b, String seed, int max) {
        List<Variant> layouts = Arrays.stream(values())
                .filter(v -> v.block == b && v.group == Group.LAYOUT).toList();
        return rotate(layouts, b.key() + "|" + (seed == null ? "" : seed), max);
    }

    /** 생성 프롬프트에 실을 배경 변형 — {@link #sample} 과 같은 방식 */
    public static List<Variant> sampleSurfaces(String seed, int max) {
        List<Variant> surfaces = Arrays.stream(values()).filter(v -> v.group == Group.SURFACE).toList();
        return rotate(surfaces, "surface|" + (seed == null ? "" : seed), max);
    }

    /**
     * 첫 번째(기본)는 **빼고**, 나머지에서 seed 로 정한 위치부터 이어서 max 개.
     *
     * ★ 왜 기본을 빼나 — 기본을 맨 앞에 "(기본)" 으로 실었더니 모델이 그걸 골랐다
     *   (Bedrock 실측: highlight 4/4 · steps 3/4 가 기본). 목록 맨 앞이 정답처럼 읽힌다.
     *   기본 모양은 class 를 안 붙이면 그대로 나오므로 목록에 있을 이유가 없다.
     */
    private static List<Variant> rotate(List<Variant> all, String seed, int max) {
        if (all.isEmpty()) return all;
        List<Variant> rest = all.subList(1, all.size());
        if (rest.size() <= max) return rest;
        int start = Math.floorMod(seed.hashCode(), rest.size());
        List<Variant> out = new java.util.ArrayList<>(max);
        for (int i = 0; i < max; i++) out.add(rest.get((start + i) % rest.size()));
        return out;
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
        // ★ 항목 수는 한 번만 센다. must 가 없는 블록(hero · cta)은 셀 것이 없다.
        int items = (b.must() == null) ? Integer.MAX_VALUE : section.select(b.must()).size();
        EnumSet<Group> used = EnumSet.noneOf(Group.class);
        for (String c : List.copyOf(section.classNames())) {
            if (!looksLike(c)) continue;
            Optional<Variant> v = find(c).filter(allowed::contains);
            if (v.isEmpty() || !used.add(v.get().group)) {
                section.removeClass(c);
                continue;
            }
            // ★ 항목이 모자란 모양은 뗀다. class 만 떼고 내용은 그대로 둔다 —
            //   기본 모양으로 그려지며, 잘린 카드보다 낫다.
            if (items < NEEDS_ITEMS.getOrDefault(v.get(), 0)) {
                section.removeClass(c);
                used.remove(v.get().group);     // 같은 묶음에서 다른 것을 쓸 자리를 돌려준다
            }
        }
    }
}
