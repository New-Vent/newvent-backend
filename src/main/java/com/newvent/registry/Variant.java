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
 *   <b>scripts/name-diff.py 가 이 짝을 CI 에서 본다.</b> 두 레포라 한쪽만 머지되기 쉽다 —
 *   실제로 프런트가 변형 59개를 먼저 넣었고 두 주 가까이 쓰이지 못했다.
 *
 * ★ 템플릿 블록(.ev-block)에는 효과가 없다
 *   event.css 의 `.v-*` 규칙은 전부 `[data-block]:not(.ev-block)` 에만 건다.
 *   템플릿은 자기 디자인(.hl-* · .sp-* …)이 있고, 섞으면 둘 다 망가진다.
 *   그래서 수정 프롬프트도 템플릿 블록에는 변형을 안내하지 않는다.
 *
 * ★ 같은 묶음(Group)에서는 하나만 남긴다 — {@link #sanitize}
 *
 * <h2>Pick — 생성에 보여줄 것과 수정에만 보여줄 것</h2>
 *
 * 90개를 전부 생성 프롬프트에 실으면 프롬프트가 7,300자에서 11,000자가 된다
 * (백지 생성 1회 ₩1.2 → ₩2). 그런데 생성은 "분위기에 맞는 것 하나" 면 충분하고,
 * 모양을 조목조목 고르는 일은 <b>수정</b>에서 일어난다 ("빤짝이 넣어줘").
 * 그리고 수정 프롬프트는 <b>블록 하나치</b>만 실으므로 전부 보여줘도 거의 안 비싸다.
 *
 * <pre>
 *   생성 프롬프트  CORE 만        (51개 — 예전 48개와 비슷해 비용이 그대로다)
 *   수정 프롬프트  CORE + EXTRA   (그 블록 것만, 평균 9개)
 *   sanitize      CORE + EXTRA   ← 둘 다 받는다. 모델이 EXTRA 를 골라도 안 지운다
 * </pre>
 *
 * EXTRA 를 고르는 걸 막지 않는다는 게 요점이다. 목록에서 빼면 지워지지만,
 * 여기 있으면 "광고는 안 하되 쓰면 받아준다" 가 된다.
 */
public enum Variant {

    // ★ 아래 "신규" 표시는 event.css 가 refactor/event-css-split 에서 먼저 들여온 것들이다.
    //   레지스트리에 이름이 없어 쓰이지 못하고 있었다.

    // ── hero ──
    HERO_CENTER(Block.HERO, Group.LAYOUT, Pick.CORE, "v-hero-center", "가운데 정렬, 그라데이션 배경"),
    HERO_POSTER(Block.HERO, Group.LAYOUT, Pick.CORE, "v-hero-poster", "아주 큰 제목, 포스터처럼 강렬하게"),
    HERO_MINIMAL(Block.HERO, Group.LAYOUT, Pick.CORE, "v-hero-minimal", "밝은 배경, 큰 글씨, 장식 없음"),
    HERO_SPLIT(Block.HERO, Group.LAYOUT, Pick.CORE, "v-hero-split", "제목은 왼쪽 크게, 소개는 오른쪽 반투명 상자에 (신규)"),
    HERO_GLASS(Block.HERO, Group.LAYOUT, Pick.CORE, "v-hero-glass", "흐린 유리판이 얹힌 화려한 배경 (신규)"),
    HERO_GRADIENT_TEXT(Block.HERO, Group.LAYOUT, Pick.CORE, "v-hero-gradient-text",
            "밝은 배경에 제목 글자 자체가 그라데이션 (신규)"),
    HERO_LEFT(Block.HERO, Group.LAYOUT, Pick.EXTRA, "v-hero-left", "왼쪽 정렬, 잡지 표지 느낌"),
    HERO_OUTLINE(Block.HERO, Group.LAYOUT, Pick.EXTRA, "v-hero-outline", "흰 배경에 브랜드색 테두리"),
    HERO_TICKET(Block.HERO, Group.LAYOUT, Pick.EXTRA, "v-hero-ticket", "양옆이 입장권처럼 파인 모양, 점선 테두리 (신규)"),
    HERO_WAVE(Block.HERO, Group.LAYOUT, Pick.EXTRA, "v-hero-wave", "아래쪽이 물결로 잘린 모양 (신규)"),

    // ── highlight ──
    // ★ v-highlight-ribbon 을 뺐다 — event.css 에 규칙이 없다. 골라도 아무 일이 안 났다.
    //   기본 띠 배너는 class 를 안 붙여도 [data-block="highlight"] 기본 규칙이 그려 준다.
    HIGHLIGHT_GLOW(Block.HIGHLIGHT, Group.LAYOUT, Pick.CORE, "v-highlight-glow", "빛나는 테두리의 어두운 배너"),
    HIGHLIGHT_NEON(Block.HIGHLIGHT, Group.LAYOUT, Pick.CORE, "v-highlight-neon",
            "거의 검은 배경에 네온처럼 빛나는 흰 글씨 (신규)"),
    HIGHLIGHT_BADGE(Block.HIGHLIGHT, Group.LAYOUT, Pick.CORE, "v-highlight-badge", "둥근 배지 안 큰 글씨"),
    // 안내 상자(콜아웃) — 경고창 모양. 아이콘은 CSS 가 붙인다
    HIGHLIGHT_ALERT(Block.HIGHLIGHT, Group.LAYOUT, Pick.CORE, "v-highlight-alert", "주의 안내 상자 (노란색, 느낌표)"),
    HIGHLIGHT_INFO(Block.HIGHLIGHT, Group.LAYOUT, Pick.CORE, "v-highlight-info", "알림 안내 상자 (파란색, i 아이콘)"),
    HIGHLIGHT_SUCCESS(Block.HIGHLIGHT, Group.LAYOUT, Pick.CORE, "v-highlight-success", "완료·혜택 안내 상자 (초록색, 체크)"),
    HIGHLIGHT_QUOTE(Block.HIGHLIGHT, Group.LAYOUT, Pick.EXTRA, "v-highlight-quote", "큰 따옴표가 붙은 인용형"),
    HIGHLIGHT_DANGER(Block.HIGHLIGHT, Group.LAYOUT, Pick.EXTRA, "v-highlight-danger",
            "경고 안내 상자 (연한 빨강, 빨간 느낌표) (신규)"),
    HIGHLIGHT_DARK(Block.HIGHLIGHT, Group.LAYOUT, Pick.EXTRA, "v-highlight-dark",
            "검정 배경에 브랜드색 왼쪽 굵은 선 (신규)"),
    HIGHLIGHT_STAMP(Block.HIGHLIGHT, Group.LAYOUT, Pick.EXTRA, "v-highlight-stamp",
            "비스듬히 기울어진 이중선 도장 모양 (신규)"),
    // ★ 글자가 가로로 흐른다. 두 번째 <p> 는 CSS 가 숨기므로 문장 하나만 쓴다.
    HIGHLIGHT_MARQUEE(Block.HIGHLIGHT, Group.LAYOUT, Pick.EXTRA, "v-highlight-marquee",
            "글자가 옆으로 흐르는 전광판. 문장 하나만 쓴다 — 두 번째 문단은 보이지 않는다 (신규)"),

    // ── intro ──
    // ★ v-intro-plain 을 뺐다 — event.css 에 규칙이 없다.
    INTRO_LEAD(Block.INTRO, Group.LAYOUT, Pick.CORE, "v-intro-lead", "첫 문단을 크게 강조"),
    INTRO_QUOTE(Block.INTRO, Group.LAYOUT, Pick.CORE, "v-intro-quote", "왼쪽 굵은 선이 있는 인용형"),
    INTRO_CENTER(Block.INTRO, Group.LAYOUT, Pick.CORE, "v-intro-center", "가운데 정렬, 여백 넉넉하게"),
    INTRO_BOXED(Block.INTRO, Group.LAYOUT, Pick.CORE, "v-intro-boxed", "소제목이 브랜드색 띠로 꽉 찬 상자 (신규)"),
    INTRO_DROPCAP(Block.INTRO, Group.LAYOUT, Pick.EXTRA, "v-intro-dropcap", "첫 글자를 아주 크게 (신문 머리글자) (신규)"),
    INTRO_SIDEBAR(Block.INTRO, Group.LAYOUT, Pick.EXTRA, "v-intro-sidebar", "소제목을 왼쪽 칸에 세로로 붙인 2단 (신규)"),
    INTRO_TWO_COL(Block.INTRO, Group.LAYOUT, Pick.EXTRA, "v-intro-two-col", "본문을 신문처럼 2단으로 (신규)"),

    // ── benefits ──
    BENEFITS_GRID(Block.BENEFITS, Group.LAYOUT, Pick.CORE, "v-benefits-grid", "2열 카드"),
    BENEFITS_LIST(Block.BENEFITS, Group.LAYOUT, Pick.CORE, "v-benefits-list", "세로 목록 (기본)"),
    BENEFITS_TICKET(Block.BENEFITS, Group.LAYOUT, Pick.CORE, "v-benefits-ticket",
            "왼쪽에 브랜드색 굵은 띠가 붙은 카드 (신규)"),
    BENEFITS_CHECKLIST(Block.BENEFITS, Group.LAYOUT, Pick.CORE, "v-benefits-checklist",
            "항목마다 동그란 체크 표시 (신규)"),
    BENEFITS_ICON(Block.BENEFITS, Group.LAYOUT, Pick.CORE, "v-benefits-icon",
            "항목마다 별 아이콘이 붙은 연한 상자 (신규)"),
    BENEFITS_COUPON(Block.BENEFITS, Group.LAYOUT, Pick.CORE, "v-benefits-coupon", "점선 테두리 쿠폰"),
    BENEFITS_SPOTLIGHT(Block.BENEFITS, Group.LAYOUT, Pick.CORE, "v-benefits-spotlight", "첫 혜택을 크게, 나머지는 작게"),
    BENEFITS_NUMBERED(Block.BENEFITS, Group.LAYOUT, Pick.EXTRA, "v-benefits-numbered", "동그라미 번호가 붙은 목록"),
    BENEFITS_STRIPE(Block.BENEFITS, Group.LAYOUT, Pick.EXTRA, "v-benefits-stripe",
            "한 줄 건너 연한 배경이 깔린 표 모양 (신규)"),
    BENEFITS_PRICING(Block.BENEFITS, Group.LAYOUT, Pick.EXTRA, "v-benefits-pricing",
            "요금제처럼 나란한 카드, 가운데가 추천으로 솟아오름 (신규)"),
    BENEFITS_THREE_COL(Block.BENEFITS, Group.LAYOUT, Pick.EXTRA, "v-benefits-three-col",
            "3열 카드. 혜택이 3개 이상일 때만 (신규)"),
    // ★ event.css 의 li { flex: 0 0 78% } 가 3개 이상을 전제로 한 값이라
    //   2개면 둘째 카드가 잘린 채 멈춘다. 프롬프트에도 같은 경고를 쓴다.
    BENEFITS_CAROUSEL(Block.BENEFITS, Group.LAYOUT, Pick.EXTRA, "v-benefits-carousel",
            "옆으로 넘기는 카드 (캐러셀). 혜택이 3개 이상일 때만"),

    // ── compare ──
    // ★ v-compare-striped 를 뺐다 — event.css 에 규칙이 없다.
    COMPARE_BORDERED(Block.COMPARE, Group.LAYOUT, Pick.CORE, "v-compare-bordered", "칸마다 테두리"),
    COMPARE_HIGHLIGHT(Block.COMPARE, Group.LAYOUT, Pick.CORE, "v-compare-highlight", "마지막 열을 추천으로 강조"),
    COMPARE_ROUNDED(Block.COMPARE, Group.LAYOUT, Pick.CORE, "v-compare-rounded", "모서리가 둥글고 그림자가 있는 표 (신규)"),
    COMPARE_MINIMAL(Block.COMPARE, Group.LAYOUT, Pick.EXTRA, "v-compare-minimal", "가로 구분선만 있는 깔끔한 표"),
    COMPARE_COMPACT(Block.COMPARE, Group.LAYOUT, Pick.EXTRA, "v-compare-compact", "글씨를 줄여 빽빽하게. 열이 많을 때 (신규)"),
    COMPARE_DARK(Block.COMPARE, Group.LAYOUT, Pick.EXTRA, "v-compare-dark", "검정 머리글에 어두운 회색 표 (신규)"),

    // ── audience ──
    // ★ v-audience-chips 를 뺐다 — event.css 에 규칙이 없다.
    //   기본 태그 칩은 class 없이도 [data-block="audience"] li 기본 규칙이 그려 준다.
    AUDIENCE_CHECKS(Block.AUDIENCE, Group.LAYOUT, Pick.CORE, "v-audience-checks", "체크 표시 목록"),
    AUDIENCE_CARDS(Block.AUDIENCE, Group.LAYOUT, Pick.CORE, "v-audience-cards", "2열 카드"),
    AUDIENCE_ICONS(Block.AUDIENCE, Group.LAYOUT, Pick.CORE, "v-audience-icons", "항목마다 사람 아이콘이 붙은 목록 (신규)"),
    AUDIENCE_OUTLINE(Block.AUDIENCE, Group.LAYOUT, Pick.EXTRA, "v-audience-outline", "테두리만 있는 태그 칩 (신규)"),
    AUDIENCE_INLINE(Block.AUDIENCE, Group.LAYOUT, Pick.EXTRA, "v-audience-inline",
            "가운뎃점으로 이어 한 줄로 쓴다. 대상이 짧을 때 (신규)"),

    // ── steps ──
    STEPS_TIMELINE(Block.STEPS, Group.LAYOUT, Pick.CORE, "v-steps-timeline", "세로 타임라인"),
    STEPS_NUMBERED(Block.STEPS, Group.LAYOUT, Pick.CORE, "v-steps-numbered", "동그라미 번호 목록"),
    STEPS_ZIGZAG(Block.STEPS, Group.LAYOUT, Pick.CORE, "v-steps-zigzag", "한 칸 건너 배경색이 바뀌는 단계 상자 (신규)"),
    STEPS_PROGRESS(Block.STEPS, Group.LAYOUT, Pick.CORE, "v-steps-progress",
            "가로 진행 막대 위 점. 단계가 3~4개이고 글이 짧을 때 (신규)"),
    STEPS_CHECKLIST(Block.STEPS, Group.LAYOUT, Pick.CORE, "v-steps-checklist", "네모 체크상자가 붙은 할 일 목록 (신규)"),
    STEPS_CARDS(Block.STEPS, Group.LAYOUT, Pick.CORE, "v-steps-cards", "가로로 나란한 카드"),
    STEPS_ARROWS(Block.STEPS, Group.LAYOUT, Pick.EXTRA, "v-steps-arrows", "화살표로 이어지는 흐름"),
    STEPS_BUBBLE(Block.STEPS, Group.LAYOUT, Pick.EXTRA, "v-steps-bubble", "말풍선 모양 단계 카드 (신규)"),
    STEPS_MINIMAL(Block.STEPS, Group.LAYOUT, Pick.EXTRA, "v-steps-minimal", "연한 큰 숫자가 뒤에 깔린 간결한 목록 (신규)"),
    STEPS_CAROUSEL(Block.STEPS, Group.LAYOUT, Pick.EXTRA, "v-steps-carousel",
            "옆으로 넘기는 단계 카드 (캐러셀). 단계가 3개 이상일 때만"),

    // ── faq ──
    // ★ v-faq-plain 을 뺐다 — event.css 에 규칙이 없다.
    // ★ 마크업이 두 벌이다 — <dl> 과 <details>. 설명에 그대로 적어 모델에게 보인다.
    FAQ_ACCORDION(Block.FAQ, Group.LAYOUT, Pick.CORE, "v-faq-accordion",
            "눌러서 펼치는 아코디언. 이걸 고르면 <div class=\"ev-accordion\"> 안에 "
            + "<details><summary>질문</summary><p>답</p></details> 를 질문마다 쓴다"),
    FAQ_CARDS(Block.FAQ, Group.LAYOUT, Pick.CORE, "v-faq-cards", "질문마다 카드"),
    FAQ_QA(Block.FAQ, Group.LAYOUT, Pick.CORE, "v-faq-qa", "Q · A 배지가 붙은 형식"),
    FAQ_LEFTBAR(Block.FAQ, Group.LAYOUT, Pick.CORE, "v-faq-leftbar", "질문 왼쪽에 브랜드색 세로선 (신규)"),
    FAQ_BRUTAL(Block.FAQ, Group.LAYOUT, Pick.EXTRA, "v-faq-brutal",
            "굵은 검정 테두리 + 그림자 아코디언. 마크업은 v-faq-accordion 과 같다"),
    FAQ_NUMBERED(Block.FAQ, Group.LAYOUT, Pick.EXTRA, "v-faq-numbered", "질문마다 Q1. Q2. 번호가 붙는다 (신규)"),
    FAQ_CHAT(Block.FAQ, Group.LAYOUT, Pick.EXTRA, "v-faq-chat", "질문·답이 주고받는 말풍선 (신규)"),
    FAQ_FLUSH(Block.FAQ, Group.LAYOUT, Pick.EXTRA, "v-faq-flush",
            "테두리 없이 줄만 있는 아코디언. 마크업은 v-faq-accordion 과 같다 (신규)"),

    // ── cta ──
    CTA_WIDE(Block.CTA, Group.LAYOUT, Pick.CORE, "v-cta-wide", "가로로 꽉 찬 버튼"),
    CTA_CENTER(Block.CTA, Group.LAYOUT, Pick.CORE, "v-cta-center", "가운데 버튼 (기본)"),
    CTA_GLOW(Block.CTA, Group.LAYOUT, Pick.CORE, "v-cta-glow", "버튼이 천천히 빛났다 꺼졌다 한다 (신규)"),
    CTA_PILL(Block.CTA, Group.LAYOUT, Pick.CORE, "v-cta-pill", "알약 모양 버튼"),
    CTA_BANNER(Block.CTA, Group.LAYOUT, Pick.CORE, "v-cta-banner", "색 배너 상자 안의 버튼"),
    CTA_ARROW(Block.CTA, Group.LAYOUT, Pick.CORE, "v-cta-arrow", "버튼 글씨 뒤에 → 화살표 (신규)"),
    CTA_OUTLINE(Block.CTA, Group.LAYOUT, Pick.EXTRA, "v-cta-outline", "테두리만 있는 버튼"),
    CTA_DARK(Block.CTA, Group.LAYOUT, Pick.EXTRA, "v-cta-dark", "검은 상자 안 흰 버튼 (신규)"),
    CTA_GRADIENT_BORDER(Block.CTA, Group.LAYOUT, Pick.EXTRA, "v-cta-gradient-border",
            "테두리가 그라데이션인 흰 버튼 (신규)"),
    CTA_RAISED(Block.CTA, Group.LAYOUT, Pick.EXTRA, "v-cta-raised", "눌리는 느낌의 입체 버튼 (신규)"),
    // ★ position: sticky 다. 미리보기 틀 안에서는 효과가 안 보일 수 있다.
    CTA_STICKY(Block.CTA, Group.LAYOUT, Pick.EXTRA, "v-cta-sticky", "스크롤해도 화면 아래에 붙어 따라온다 (신규)"),

    // ── stats (신규 블록) ──
    STATS_BAR(Block.STATS, Group.LAYOUT, Pick.CORE, "v-stats-bar", "숫자 아래에 막대가 그려진 카드 (신규)"),
    STATS_RING(Block.STATS, Group.LAYOUT, Pick.CORE, "v-stats-ring", "숫자를 동그란 테두리로 감싼다 (신규)"),
    STATS_INLINE(Block.STATS, Group.LAYOUT, Pick.EXTRA, "v-stats-inline",
            "한 줄에 나란히, 사이에 세로 구분선. 숫자가 2~3개일 때 (신규)"),

    // ── prize (신규 블록) ──
    PRIZE_CARDS(Block.PRIZE, Group.LAYOUT, Pick.CORE, "v-prize-cards", "2열 카드 (신규)"),
    // ★ 시상대는 1·2·3등 자리를 CSS 가 정한다. 네 번째부터는 아래에 한 줄로 깔린다
    PRIZE_PODIUM(Block.PRIZE, Group.LAYOUT, Pick.CORE, "v-prize-podium",
            "시상대 모양 — 1등을 가운데 높게. 경품이 3개일 때 제일 좋다 (신규)"),
    PRIZE_RIBBON(Block.PRIZE, Group.LAYOUT, Pick.EXTRA, "v-prize-ribbon", "첫 경품에 ★ 리본이 붙는다 (신규)"),

    // ── coupon (신규 블록) ──
    COUPON_CARD(Block.COUPON, Group.LAYOUT, Pick.CORE, "v-coupon-card", "모서리가 둥근 카드형 쿠폰 (신규)"),
    COUPON_DASHED(Block.COUPON, Group.LAYOUT, Pick.CORE, "v-coupon-dashed", "점선 테두리 쿠폰 (신규)"),
    COUPON_STAMP(Block.COUPON, Group.LAYOUT, Pick.EXTRA, "v-coupon-stamp", "도장 찍힌 듯한 쿠폰 (신규)"),

    // ── schedule (신규 블록) ──
    SCHEDULE_CARDS(Block.SCHEDULE, Group.LAYOUT, Pick.CORE, "v-schedule-cards", "일정마다 카드 (신규)"),
    SCHEDULE_CHIPS(Block.SCHEDULE, Group.LAYOUT, Pick.CORE, "v-schedule-chips", "둥근 알약으로 늘어놓는다 (신규)"),
    SCHEDULE_TABLE(Block.SCHEDULE, Group.LAYOUT, Pick.EXTRA, "v-schedule-table", "표처럼 줄맞춰 보여준다 (신규)"),

    // ── 배경 — 내용 블록 공통 ──
    SURFACE_CARD(null, Group.SURFACE, Pick.CORE, "v-surface-card", "흰 카드 배경 (기본)"),
    SURFACE_TINT(null, Group.SURFACE, Pick.CORE, "v-surface-tint", "연한 브랜드색 배경"),
    SURFACE_DARK(null, Group.SURFACE, Pick.CORE, "v-surface-dark", "어두운 배경, 흰 글씨"),
    SURFACE_PLAIN(null, Group.SURFACE, Pick.CORE, "v-surface-plain", "배경 없이 본문만"),
    SURFACE_GRADIENT(null, Group.SURFACE, Pick.CORE, "v-surface-gradient", "위에서 아래로 옅어지는 브랜드색 (신규)"),
    SURFACE_DOTS(null, Group.SURFACE, Pick.CORE, "v-surface-dots", "작은 점이 촘촘히 깔린 배경 (신규)"),
    SURFACE_GLASS(null, Group.SURFACE, Pick.EXTRA, "v-surface-glass", "반투명 유리 느낌 (신규)"),
    SURFACE_OUTLINE(null, Group.SURFACE, Pick.EXTRA, "v-surface-outline", "배경 없이 브랜드색 굵은 테두리만 (신규)"),
    SURFACE_SHADOW(null, Group.SURFACE, Pick.EXTRA, "v-surface-shadow", "테두리 없이 그림자로 띄운 카드 (신규)"),
    SURFACE_STRIPES(null, Group.SURFACE, Pick.EXTRA, "v-surface-stripes", "비스듬한 줄무늬 배경 (신규)");

    /** 같은 묶음에서는 하나만 쓴다 */
    public enum Group {
        /** 블록 전용 배치·모양 */
        LAYOUT,
        /** 블록 배경 — {@link #SURFACE_BLOCKS} 에만 */
        SURFACE
    }

    /**
     * 어디까지 보여줄 것인가. <b>둘 다 {@link #sanitize} 는 통과한다.</b>
     *
     * ★ EXTRA 를 "쓰지 마라" 가 아니라 "먼저 권하지 않는다" 로 둔 이유
     *   목록에서 아예 빼면 모델이 골랐을 때 정화가 지우고, 화면은 그대로인데
     *   "반영했어요" 가 나간다 — 조용한 실패다. 여기 두면 받아준다.
     */
    public enum Pick {
        /** 생성·수정 둘 다에 보여준다 */
        CORE,
        /** 수정에만 보여준다. 생성 프롬프트 길이를 아끼려고 뺀 것들 */
        EXTRA
    }

    /** 변형 class 의 공통 접두사. 정화와 class 비교에서 이걸로 알아본다 */
    public static final String PREFIX = "v-";

    /**
     * 배경 변형을 쓸 수 있는 블록.
     * hero · highlight · cta 는 자기 배경이 곧 디자인이라 뺀다. notices 는 서버 소유다.
     */
    // ★ coupon 은 뺀다 — 자기 배경(그라데이션 티켓)이 곧 디자인이라 hero · cta 와 같다.
    //   stats · prize · schedule 은 카드 안에 들어가는 블록이라 배경을 받는다.
    private static final Set<Block> SURFACE_BLOCKS =
            EnumSet.of(Block.INTRO, Block.STATS, Block.BENEFITS, Block.PRIZE,
                    Block.COMPARE, Block.AUDIENCE, Block.STEPS, Block.SCHEDULE, Block.FAQ);

    private final Block block;
    private final Group group;
    private final Pick pick;
    private final String cssClass;
    private final String desc;

    Variant(Block block, Group group, Pick pick, String cssClass, String desc) {
        this.block = block;
        this.group = group;
        this.pick = pick;
        this.cssClass = cssClass;
        this.desc = desc;
    }

    public Block block()     { return block; }
    public Group group()     { return group; }
    public Pick pick()       { return pick; }
    public String cssClass() { return cssClass; }
    public String desc()     { return desc; }

    /** 그 블록에 쓸 수 있는 변형 전부 — 전용 배치 먼저, 배경 나중 */
    public static List<Variant> of(Block b) {
        return Arrays.stream(values())
                .filter(v -> v.block == b || (v.group == Group.SURFACE && SURFACE_BLOCKS.contains(b)))
                .toList();
    }

    /**
     * 생성 프롬프트에 실을 것만 — {@link Pick#CORE}.
     *
     * ★ 수정은 {@link #of} 를 그대로 쓴다. 블록 하나치라 전부 실어도 싸고,
     *   모양을 조목조목 고르는 일이 거기서 일어난다.
     */
    public static List<Variant> coreOf(Block b) {
        return of(b).stream().filter(v -> v.pick == Pick.CORE).toList();
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
     * ★ Pick 은 보지 않는다. EXTRA 를 골라도 받아준다 — Pick 주석 참고.
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
