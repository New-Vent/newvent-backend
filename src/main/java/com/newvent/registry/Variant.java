package com.newvent.registry;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
 *
 * ★★ 기본(core)과 확장 — 팔레트와 같은 구조다 ({@link Pickable})
 *   지금 있는 것은 전부 기본이다. 생성에는 표본({@link #sample})으로, 수정에는 전부 실린다.
 *   확장은 요청문에 그 이름이 나올 때만 실린다({@link #matched}). 변형을 늘려도 표본이 묽어지지 않는다.
 *   검증 쪽({@link #find} · {@link #sanitize} · BlockValidator.cleanLooks)은 확장도 안다.
 *   확장은 맨 뒤의 6인자 생성자로 넣고, desc 를 "이름 — 설명" 꼴로 쓴다 — 열쇠말은 이름에서만 뽑는다.
 */
public enum Variant implements Pickable {

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
    HERO_BADGE_TOP(Block.HERO, Group.LAYOUT, "v-hero-badge-top",
            "알약 꼬리말 — 제목 위 꼬리말(선착순 · 신규 전용)을 크고 진한 알약으로", false, false),
    HERO_DIAGONAL(Block.HERO, Group.LAYOUT, "v-hero-diagonal",
            "사선 자르기 — 아래쪽이 비스듬히 잘린 배너", false, false),
    HERO_STRIPE_BG(Block.HERO, Group.LAYOUT, "v-hero-stripe-bg",
            "줄무늬 — 그라데이션 위에 사선 줄무늬가 깔린 배너", false, false),
    HERO_BRUTAL(Block.HERO, Group.LAYOUT, "v-hero-brutal",
            "브루탈 — 밝은 바탕, 두꺼운 검정 테두리와 어긋난 그림자", false, false),
    HERO_RETRO(Block.HERO, Group.LAYOUT, "v-hero-retro",
            "레트로 — 밝은 바탕에 햇살 무늬, 겹친 그림자 제목", false, false),
    HERO_PAPER_CUT(Block.HERO, Group.LAYOUT, "v-hero-paper-cut",
            "종이 오림 · 페이퍼컷 — 아래에 종이를 오려 겹친 듯한 물결 층", false, false),
    HERO_MESH(Block.HERO, Group.LAYOUT, "v-hero-mesh",
            "메시 그라데이션 · 몽환 — 여러 색이 번지는 메시 그라데이션 배경", false, false),
    HERO_GRAIN(Block.HERO, Group.LAYOUT, "v-hero-grain",
            "노이즈 · 그레인 · 필름 — 그라데이션 위에 거친 입자 질감", false, false),
    HERO_BLOB(Block.HERO, Group.LAYOUT, "v-hero-blob",
            "블롭 · 몽글몽글 — 모서리에 둥근 덩어리 장식", false, false),
    HERO_ZIGZAG_EDGE(Block.HERO, Group.LAYOUT, "v-hero-zigzag-edge",
            "톱니 · 톱날 — 아래 끝이 톱니 모양으로 잘린 배너", false, false),
    HERO_ANIM_SNOWFALL(Block.HERO, Group.MOTION, "v-anim-snowfall",
            "눈송이 · 함박눈 · 눈 내리는 — 첫 화면에 눈이 내림", false, false),
    HERO_ANIM_PETAL_FALL(Block.HERO, Group.MOTION, "v-anim-petal-fall",
            "꽃잎 · 벚꽃 · 흩날리 — 첫 화면에 분홍 꽃잎이 흩날림", false, false),
    HERO_ANIM_LEAF_FALL(Block.HERO, Group.MOTION, "v-anim-leaf-fall",
            "낙엽 · 단풍 · 가을잎 — 첫 화면에 나뭇잎이 떨어짐", false, false),

    // ── highlight ──
    HIGHLIGHT_RIBBON(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-ribbon", "가로 띠 배너 (기본)"),
    HIGHLIGHT_BADGE(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-badge", "둥근 배지 안 큰 글씨"),
    HIGHLIGHT_QUOTE(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-quote", "큰 따옴표가 붙은 인용형"),
    HIGHLIGHT_GLOW(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-glow", "빛나는 테두리의 어두운 배너"),
    HIGHLIGHT_ALERT(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-alert", "주의 안내 상자 (노란색, 느낌표)"),
    HIGHLIGHT_INFO(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-info", "알림 안내 상자 (파란색, i 아이콘)"),
    HIGHLIGHT_SUCCESS(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-success", "완료·혜택 안내 상자 (초록색, 체크)"),
    HIGHLIGHT_DANGER(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-danger", "경고 안내 상자 (빨간색, 마감 · 주의)"),
    HIGHLIGHT_DARK(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-dark", "검정 안내 상자"),
    HIGHLIGHT_MARQUEE(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-marquee", "옆으로 흐르는 띠 (첫 문장만, 움직임 줄이기 설정이면 멈춤)"),
    HIGHLIGHT_STAMP(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-stamp", "기울어진 도장 모양"),
    HIGHLIGHT_NEON(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-neon", "어두운 바탕의 네온 글씨"),
    HIGHLIGHT_SPEECH(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-speech",
            "말풍선 — 왼쪽 아래에 꼬리가 달린 말풍선 배너", false, false),
    HIGHLIGHT_POSTIT(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-postit",
            "포스트잇 · 메모지 — 노란 메모지를 살짝 기울여 붙인 모양", false, false),
    HIGHLIGHT_TAPE(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-tape",
            "테이프 메모 · 마스킹테이프 — 흰 메모 위에 테이프를 붙인 모양", false, false),
    HIGHLIGHT_STICKER(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-sticker",
            "스티커 — 기울어진 둥근 스티커, 점선 테두리", false, false),
    HIGHLIGHT_GRADIENT_BORDER(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-gradient-border",
            "그라데이션 테두리 — 흰 상자에 그라데이션 테두리", false, false),
    HIGHLIGHT_SPLIT_COLOR(Block.HIGHLIGHT, Group.LAYOUT, "v-highlight-split-color",
            "반반 · 투톤 — 왼쪽 진한 색 · 오른쪽 연한 색으로 반씩 나눈 띠", false, false),

    // ── intro ──
    INTRO_PLAIN(Block.INTRO, Group.LAYOUT, "v-intro-plain", "평범한 본문 (기본)"),
    INTRO_QUOTE(Block.INTRO, Group.LAYOUT, "v-intro-quote", "왼쪽 굵은 선이 있는 인용형"),
    INTRO_CENTER(Block.INTRO, Group.LAYOUT, "v-intro-center", "가운데 정렬, 여백 넉넉하게"),
    INTRO_LEAD(Block.INTRO, Group.LAYOUT, "v-intro-lead", "첫 문단을 크게 강조"),
    INTRO_DROPCAP(Block.INTRO, Group.LAYOUT, "v-intro-dropcap", "첫 글자를 크게 (잡지 본문)"),
    INTRO_TWO_COL(Block.INTRO, Group.LAYOUT, "v-intro-two-col", "문단을 2단으로"),
    INTRO_BOXED(Block.INTRO, Group.LAYOUT, "v-intro-boxed", "머리띠가 있는 카드 상자 (Bootstrap card)"),
    INTRO_SIDEBAR(Block.INTRO, Group.LAYOUT, "v-intro-sidebar", "왼쪽 소제목 · 오른쪽 본문"),
    INTRO_LETTER(Block.INTRO, Group.LAYOUT, "v-intro-letter",
            "편지 — 줄 그어진 편지지에 쓴 인사말", false, false),
    INTRO_ICON_ROW(Block.INTRO, Group.LAYOUT, "v-intro-icon-row",
            "별표 칸 — 문단마다 별 표시가 붙은 칸으로 나란히", false, false),

    // ── benefits ──
    BENEFITS_LIST(Block.BENEFITS, Group.LAYOUT, "v-benefits-list", "세로 목록 (기본)"),
    BENEFITS_GRID(Block.BENEFITS, Group.LAYOUT, "v-benefits-grid", "2열 카드"),
    BENEFITS_COUPON(Block.BENEFITS, Group.LAYOUT, "v-benefits-coupon", "점선 테두리 쿠폰"),
    BENEFITS_NUMBERED(Block.BENEFITS, Group.LAYOUT, "v-benefits-numbered", "동그라미 번호가 붙은 목록"),
    BENEFITS_SPOTLIGHT(Block.BENEFITS, Group.LAYOUT, "v-benefits-spotlight", "첫 혜택을 크게, 나머지는 작게"),
    BENEFITS_CAROUSEL(Block.BENEFITS, Group.LAYOUT, "v-benefits-carousel", "옆으로 넘기는 카드 (캐러셀)"),
    BENEFITS_TICKET(Block.BENEFITS, Group.LAYOUT, "v-benefits-ticket", "왼쪽 띠가 있는 티켓 카드"),
    BENEFITS_ICON(Block.BENEFITS, Group.LAYOUT, "v-benefits-icon", "항목 앞 원형 별 아이콘"),
    BENEFITS_PRICING(Block.BENEFITS, Group.LAYOUT, "v-benefits-pricing", "요금표처럼 큰 카드 (가운데 강조)"),
    BENEFITS_CHECKLIST(Block.BENEFITS, Group.LAYOUT, "v-benefits-checklist", "체크 표시 목록"),
    BENEFITS_STRIPE(Block.BENEFITS, Group.LAYOUT, "v-benefits-stripe", "한 줄씩 색이 번갈아 바뀌는 목록"),
    BENEFITS_THREE_COL(Block.BENEFITS, Group.LAYOUT, "v-benefits-three-col", "3열 카드"),
    BENEFITS_BENTO(Block.BENEFITS, Group.LAYOUT, "v-benefits-bento", "벤토 격자 — 첫 혜택은 큰 칸, 나머지는 작은 칸"),
    BENEFITS_RECEIPT(Block.BENEFITS, Group.LAYOUT, "v-benefits-receipt",
            "영수증 — 점선으로 나뉜 줄, 아래 끝이 톱니인 영수증", false, false),
    BENEFITS_TAG(Block.BENEFITS, Group.LAYOUT, "v-benefits-tag",
            "가격표 · 꼬리표 — 구멍 뚫린 꼬리표 모양 카드", false, false),
    BENEFITS_BADGE_CORNER(Block.BENEFITS, Group.LAYOUT, "v-benefits-badge-corner",
            "모서리 리본 — 첫 혜택 카드 오른쪽 위에 비스듬한 리본", false, false),
    BENEFITS_STACK(Block.BENEFITS, Group.LAYOUT, "v-benefits-stack",
            "겹친 카드 · 겹쳐 쌓기 — 살짝 기울어 겹쳐 쌓인 카드", false, false),
    BENEFITS_TIERED(Block.BENEFITS, Group.LAYOUT, "v-benefits-tiered",
            "등급별 · 실버 · 골드 — 아래로 갈수록 색이 진해지는 단계 카드", false, false),
    BENEFITS_BRUTAL(Block.BENEFITS, Group.LAYOUT, "v-benefits-brutal",
            "브루탈 — 두꺼운 검정 테두리와 어긋난 그림자 카드", false, false),
    BENEFITS_MASONRY(Block.BENEFITS, Group.LAYOUT, "v-benefits-masonry",
            "벽돌 · 메이슨리 · 핀터레스트 — 높이가 다른 카드를 2단으로 빈틈없이", false, false),
    BENEFITS_GIFT(Block.BENEFITS, Group.LAYOUT, "v-benefits-gift",
            "포장 · 리본 포장 — 카드마다 리본을 두른 선물 상자 모양", false, false),

    // ── compare ──
    COMPARE_STRIPED(Block.COMPARE, Group.LAYOUT, "v-compare-striped", "줄무늬 표 (기본)"),
    COMPARE_BORDERED(Block.COMPARE, Group.LAYOUT, "v-compare-bordered", "칸마다 테두리"),
    COMPARE_HIGHLIGHT(Block.COMPARE, Group.LAYOUT, "v-compare-highlight", "마지막 열을 추천으로 강조"),
    COMPARE_MINIMAL(Block.COMPARE, Group.LAYOUT, "v-compare-minimal", "가로 구분선만 있는 깔끔한 표"),
    COMPARE_DARK(Block.COMPARE, Group.LAYOUT, "v-compare-dark", "어두운 표 (Bootstrap table-dark)"),
    COMPARE_COMPACT(Block.COMPARE, Group.LAYOUT, "v-compare-compact", "촘촘한 작은 표 (Bootstrap table-sm)"),
    COMPARE_ROUNDED(Block.COMPARE, Group.LAYOUT, "v-compare-rounded", "그림자 있는 둥근 카드형 표"),
    COMPARE_PRICING(Block.COMPARE, Group.LAYOUT, "v-compare-pricing",
            "가격 카드 · 프라이싱 — 열마다 따로 떨어진 카드처럼, 머리글은 진한 색", false, false),
    COMPARE_CHECK_MATRIX(Block.COMPARE, Group.LAYOUT, "v-compare-check-matrix",
            "체크 표 · O X — 값 칸의 ✓ · ✗ 를 가운데에 크게 (요청문에 있는 값만)", false, false),
    COMPARE_VS(Block.COMPARE, Group.LAYOUT, "v-compare-vs",
            "VS · 대결 — 두 열을 다른 색으로 맞세우고 머리글 사이에 VS", false, false),

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
    STEPS_PROGRESS(Block.STEPS, Group.LAYOUT, "v-steps-progress", "가로 진행 막대 위 단계 점"),
    STEPS_CHECKLIST(Block.STEPS, Group.LAYOUT, "v-steps-checklist", "체크 상자 목록"),
    STEPS_ZIGZAG(Block.STEPS, Group.LAYOUT, "v-steps-zigzag", "좌우로 번갈아 놓인 카드"),
    STEPS_BUBBLE(Block.STEPS, Group.LAYOUT, "v-steps-bubble", "말풍선 단계"),
    STEPS_MINIMAL(Block.STEPS, Group.LAYOUT, "v-steps-minimal", "큰 번호만 있는 깔끔한 목록"),
    STEPS_STEPPER(Block.STEPS, Group.LAYOUT, "v-steps-stepper",
            "스테퍼 — 번호 동그라미를 선으로 이은 가로 단계 (좁으면 세로)", false, false),
    STEPS_BADGE(Block.STEPS, Group.LAYOUT, "v-steps-badge",
            "STEP 알약 · 스텝 — 단계마다 STEP 01 알약이 붙은 카드", false, false),
    STEPS_NUMBERS_BIG(Block.STEPS, Group.LAYOUT, "v-steps-numbers-big",
            "워터마크 번호 — 단계 번호를 배경에 아주 크게 흐리게", false, false),
    STEPS_COMIC(Block.STEPS, Group.LAYOUT, "v-steps-comic",
            "만화 · 웹툰 — 단계마다 굵은 테두리 만화 칸", false, false),

    // ── faq ──
    FAQ_PLAIN(Block.FAQ, Group.LAYOUT, "v-faq-plain", "구분선 목록 (기본)"),
    FAQ_CARDS(Block.FAQ, Group.LAYOUT, "v-faq-cards", "질문마다 카드"),
    FAQ_QA(Block.FAQ, Group.LAYOUT, "v-faq-qa", "Q · A 배지가 붙은 형식"),
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
    FAQ_TWO_COL(Block.FAQ, Group.LAYOUT, "v-faq-two-col",
            "2단 — 질문과 답을 두 단으로 나눠 흘림 (목록형 dl)", false, false),
    FAQ_SIDEBAR(Block.FAQ, Group.LAYOUT, "v-faq-sidebar",
            "사이드 제목 · 사이드바 — 소제목은 왼쪽, 질문 목록은 오른쪽", false, false),
    FAQ_GRID_CARDS(Block.FAQ, Group.LAYOUT, "v-faq-grid-cards",
            "격자 · 타일 — 질문과 답을 작은 카드 격자로 (목록형 dl)", false, false),
    FAQ_HIGHLIGHT_TOP(Block.FAQ, Group.LAYOUT, "v-faq-highlight-top",
            "베스트 · 상위 3개 — 앞의 세 질문을 크게, 나머지는 작게 (목록형 dl)", false, false),

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
    CTA_SHINE(Block.CTA, Group.LAYOUT, "v-cta-shine",
            "빛 훑기 · 반짝 · 샤인 — 빛이 버튼 위를 주기적으로 훑고 지나감", false, false),
    CTA_PULSE_RING(Block.CTA, Group.LAYOUT, "v-cta-pulse-ring",
            "퍼지는 고리 · 파동 — 버튼 바깥으로 고리가 퍼져 나감", false, false),
    CTA_BRUTAL(Block.CTA, Group.LAYOUT, "v-cta-brutal",
            "브루탈 — 두꺼운 검정 테두리와 어긋난 그림자 버튼", false, false),
    CTA_STAMP(Block.CTA, Group.LAYOUT, "v-cta-stamp",
            "도장 · 스탬프 — 기울어진 도장을 찍은 듯한 버튼", false, false),
    CTA_TICKET(Block.CTA, Group.LAYOUT, "v-cta-ticket",
            "티켓 · 입장권 — 양옆이 파인 티켓 모양 버튼", false, false),
    CTA_ANIM_PULSE(Block.CTA, Group.MOTION, "v-anim-pulse",
            "맥박 · 두근두근 · 펄스 — 버튼이 천천히 커졌다 작아지기를 되풀이", false, false),
    CTA_ANIM_HEART_BEAT(Block.CTA, Group.MOTION, "v-anim-heart-beat",
            "심장 박동 · 하트비트 · 콩닥 — 버튼이 두 번씩 콩닥 뛰고 쉼", false, false),

    // ── stats ──
    STATS_GRID(Block.STATS, Group.LAYOUT, "v-stats-grid", "2열 큰 숫자 카드 (기본)"),
    STATS_INLINE(Block.STATS, Group.LAYOUT, "v-stats-inline", "한 줄에 나란히"),
    STATS_RING(Block.STATS, Group.LAYOUT, "v-stats-ring", "원형 테두리 안 숫자"),
    STATS_BAR(Block.STATS, Group.LAYOUT, "v-stats-bar", "숫자 아래 강조 막대"),
    STATS_BENTO(Block.STATS, Group.LAYOUT, "v-stats-bento", "벤토 격자 — 첫 숫자는 큰 칸, 나머지는 작은 칸"),
    STATS_BIG_NUMBER(Block.STATS, Group.LAYOUT, "v-stats-big-number",
            "초대형 · 대문짝 — 첫 숫자 하나를 한 줄 가득 아주 크게", false, false),
    STATS_DIVIDED(Block.STATS, Group.LAYOUT, "v-stats-divided",
            "구분선 — 카드 없이 숫자 사이에 세로 줄만", false, false),
    STATS_BADGE(Block.STATS, Group.LAYOUT, "v-stats-badge",
            "동그라미 · 원형 배지 — 숫자를 꽉 찬 동그라미 안에", false, false),
    STATS_FLIP(Block.STATS, Group.LAYOUT, "v-stats-flip",
            "플립 시계 · 넘김판 — 숫자를 넘김식 숫자판처럼", false, false),

    // ── prize ──
    PRIZE_LIST(Block.PRIZE, Group.LAYOUT, "v-prize-list", "등수 목록 (기본)"),
    PRIZE_PODIUM(Block.PRIZE, Group.LAYOUT, "v-prize-podium", "1 · 2 · 3등 단상 (앞의 세 항목)"),
    PRIZE_CARDS(Block.PRIZE, Group.LAYOUT, "v-prize-cards", "경품 카드"),
    PRIZE_RIBBON(Block.PRIZE, Group.LAYOUT, "v-prize-ribbon", "첫 항목에 리본을 단 1등 강조"),
    PRIZE_BENTO(Block.PRIZE, Group.LAYOUT, "v-prize-bento", "벤토 격자 — 첫 경품은 큰 칸, 나머지는 작은 칸"),
    PRIZE_MEDAL(Block.PRIZE, Group.LAYOUT, "v-prize-medal",
            "메달 — 앞의 세 항목에 금 · 은 · 동 메달", false, false),
    PRIZE_SHOWCASE(Block.PRIZE, Group.LAYOUT, "v-prize-showcase",
            "진열대 · 쇼케이스 — 경품을 받침대 위 칸에 나란히", false, false),
    PRIZE_LUCKY_BAG(Block.PRIZE, Group.LAYOUT, "v-prize-lucky-bag",
            "복주머니 — 경품마다 빨간 복주머니 그림, 설 · 명절 분위기", false, false),

    // ── coupon ──
    COUPON_TICKET(Block.COUPON, Group.LAYOUT, "v-coupon-ticket", "양옆이 파인 쿠폰 티켓 (기본)"),
    COUPON_DASHED(Block.COUPON, Group.LAYOUT, "v-coupon-dashed", "점선 쿠폰 상자"),
    COUPON_STAMP(Block.COUPON, Group.LAYOUT, "v-coupon-stamp", "도장이 찍힌 쿠폰"),
    COUPON_CARD(Block.COUPON, Group.LAYOUT, "v-coupon-card", "어두운 카드형 쿠폰"),
    COUPON_BARCODE(Block.COUPON, Group.LAYOUT, "v-coupon-barcode",
            "바코드 — 아래에 흐린 바코드 줄무늬 장식 (찍히지 않는 장식)", false, false),
    COUPON_BOARDING_PASS(Block.COUPON, Group.LAYOUT, "v-coupon-boarding-pass",
            "항공권 · 탑승권 · 보딩패스 — 오른쪽에 비행기 절취 칸", false, false),
    COUPON_STAMP_CARD(Block.COUPON, Group.LAYOUT, "v-coupon-stamp-card",
            "적립 카드 · 스탬프 — 아래에 빈 도장 칸이 줄지어 있는 적립 카드", false, false),
    COUPON_RECEIPT(Block.COUPON, Group.LAYOUT, "v-coupon-receipt",
            "영수증 — 흰 영수증 종이 위의 쿠폰, 아래 끝이 톱니", false, false),

    // ── schedule ──
    SCHEDULE_TIMELINE(Block.SCHEDULE, Group.LAYOUT, "v-schedule-timeline", "세로 타임라인 (기본)"),
    SCHEDULE_CHIPS(Block.SCHEDULE, Group.LAYOUT, "v-schedule-chips", "날짜 칩"),
    SCHEDULE_TABLE(Block.SCHEDULE, Group.LAYOUT, "v-schedule-table", "표처럼 줄 맞춤"),
    SCHEDULE_CARDS(Block.SCHEDULE, Group.LAYOUT, "v-schedule-cards", "가로 카드"),
    SCHEDULE_ROADMAP(Block.SCHEDULE, Group.LAYOUT, "v-schedule-roadmap",
            "로드맵 — 가로로 이어진 선 위에 일정 점", false, false),
    SCHEDULE_TICKET(Block.SCHEDULE, Group.LAYOUT, "v-schedule-ticket",
            "티켓 — 일정마다 양옆이 파인 티켓, 날짜와 설명 사이 절취선", false, false),
    SCHEDULE_VERTICAL_CENTER(Block.SCHEDULE, Group.LAYOUT, "v-schedule-vertical-center",
            "양쪽 타임라인 · 가운데 타임라인 — 가운데 세로선을 두고 일정이 좌우로 번갈아", false, false),
    SURFACE_CARD(null, Group.SURFACE, "v-surface-card", "흰 카드 배경 (기본)"),
    SURFACE_TINT(null, Group.SURFACE, "v-surface-tint", "연한 브랜드색 배경"),
    SURFACE_DARK(null, Group.SURFACE, "v-surface-dark", "어두운 배경, 흰 글씨"),
    SURFACE_PLAIN(null, Group.SURFACE, "v-surface-plain", "배경 없이 본문만"),
    SURFACE_GRADIENT(null, Group.SURFACE, "v-surface-gradient", "연한 그라데이션 배경"),
    SURFACE_GLASS(null, Group.SURFACE, "v-surface-glass", "반투명 유리"),
    SURFACE_DOTS(null, Group.SURFACE, "v-surface-dots", "점무늬 배경"),
    SURFACE_STRIPES(null, Group.SURFACE, "v-surface-stripes", "사선 무늬 배경"),
    SURFACE_OUTLINE(null, Group.SURFACE, "v-surface-outline", "브랜드색 테두리만"),
    SURFACE_SHADOW(null, Group.SURFACE, "v-surface-shadow", "떠 있는 그림자 카드"),
    SURFACE_BLUE(null, Group.SURFACE, "v-surface-blue", "파란색 배경 (연한 파랑)", true),
    SURFACE_GREEN(null, Group.SURFACE, "v-surface-green", "초록색 배경 (연한 초록)", true),
    SURFACE_YELLOW(null, Group.SURFACE, "v-surface-yellow", "노란색 배경 (연한 노랑)", true),
    SURFACE_ORANGE(null, Group.SURFACE, "v-surface-orange", "주황색 배경 (연한 주황)", true),
    SURFACE_RED(null, Group.SURFACE, "v-surface-red", "빨간색 배경 (연한 빨강)", true),
    SURFACE_PURPLE(null, Group.SURFACE, "v-surface-purple", "보라색 배경 (연한 보라)", true),
    SURFACE_PINK(null, Group.SURFACE, "v-surface-pink", "분홍색 배경 (연한 분홍)", true),
    SURFACE_GRAY(null, Group.SURFACE, "v-surface-gray", "회색 배경 (연한 회색)", true),
    SURFACE_SNOW(null, Group.SURFACE, "v-surface-snow",
            "눈송이 무늬 · 겨울 — 흩어진 눈송이 무늬", false, false),
    SURFACE_PETALS(null, Group.SURFACE, "v-surface-petals",
            "꽃잎 무늬 · 벚꽃잎 — 흩어진 벚꽃잎 무늬", false, false),
    SURFACE_LEAVES(null, Group.SURFACE, "v-surface-leaves",
            "낙엽 무늬 · 단풍 · 가을 — 흩어진 낙엽 무늬", false, false),
    SURFACE_HEARTS(null, Group.SURFACE, "v-surface-hearts",
            "하트 무늬 · 하트 — 작은 하트 무늬", false, false),
    SURFACE_STARS(null, Group.SURFACE, "v-surface-stars",
            "별 무늬 · 반짝이 — 작은 별 무늬", false, false),
    SURFACE_HANJI(null, Group.SURFACE, "v-surface-hanji",
            "한지 · 전통 — 한지 결이 보이는 질감", false, false),
    SURFACE_PAPER(null, Group.SURFACE, "v-surface-paper",
            "종이 질감 · 페이퍼 — 은은한 종이 결", false, false),
    SURFACE_KRAFT(null, Group.SURFACE, "v-surface-kraft",
            "크라프트지 · 크라프트 — 갈색 크라프트 종이", false, false),
    SURFACE_GRID(null, Group.SURFACE, "v-surface-grid",
            "모눈 · 방안지 · 격자 무늬 — 연한 모눈선", false, false),
    SURFACE_CONFETTI(null, Group.SURFACE, "v-surface-confetti",
            "색종이 · 컨페티 · 파티 — 흩뿌린 색종이 무늬", false, false),
    SURFACE_GRAIN(null, Group.SURFACE, "v-surface-grain",
            "노이즈 · 그레인 — 거친 입자 질감", false, false),
    SURFACE_WAVE_TOP(null, Group.SURFACE, "v-surface-wave-top",
            "위 물결 · 물결 경계 — 위쪽이 물결 모양으로 시작", false, false),
    SURFACE_WAVE_BOTTOM(null, Group.SURFACE, "v-surface-wave-bottom",
            "아래 물결 · 물결 경계 — 아래쪽이 물결 모양으로 끝", false, false),
    SURFACE_ZIGZAG(null, Group.SURFACE, "v-surface-zigzag",
            "톱니 경계 · 톱날 — 위아래 끝이 톱니 모양", false, false),
    SURFACE_BRUTAL(null, Group.SURFACE, "v-surface-brutal",
            "브루탈 — 두꺼운 검정 테두리와 어긋난 그림자 상자", false, false),
    SURFACE_NOTEBOOK(null, Group.SURFACE, "v-surface-notebook",
            "공책 · 줄노트 — 공책처럼 가로줄이 그어진 바탕", false, false),
    ANIM_FADE_UP(null, Group.MOTION, "v-anim-fade-up",
            "페이드인 · 슬라이드업 · 떠오르기 — 영역이 아래에서 떠오르며 나타남", false, false),
    ANIM_ZOOM_IN(null, Group.MOTION, "v-anim-zoom-in",
            "줌인 · 커지며 나타나기 — 영역이 조금 작게 시작해 커지며 나타남", false, false),
    ANIM_STAGGER(null, Group.MOTION, "v-anim-stagger",
            "차례로 나타나기 · 순차 — 목록 항목이 하나씩 차례로 나타남", false, false);


    /** 같은 묶음에서는 하나만 쓴다 */
    public enum Group {
        /** 블록 전용 배치·모양 */
        LAYOUT,
        /** 블록 배경 — {@link #SURFACE_BLOCKS} 에만 */
        SURFACE,
        /** 움직임 — 블록 전용(hero · cta) 이거나, block 이 null 이면 {@link #MOTION_BLOCKS} 공통 */
        MOTION
    }

    /** 변형 class 의 공통 접두사. 정화와 class 비교에서 이걸로 알아본다 */
    public static final String PREFIX = "v-";

    /** 요청문이 부른 확장 변형을 블록마다(배경은 페이지 전체에서) 몇 개까지 더 싣나 */
    static final int MAX_MATCHED = 2;

    /**
     * 이름에서 열쇠말로 쓰지 않는 말 — 영역 · 모양을 가리키는 흔한 말이다.
     * ★ "겹친 카드" 의 "카드" 가 "카드 혜택" 요청에 걸리면 아무 영역에나 끌려온다.
     *   변형은 이름만 보므로(설명은 안 봄) 이름에 흔히 들어가는 말을 여기서 막는다.
     */
    private static final Set<String> NOT_KEYWORDS = Set.of(
            "기본", "카드", "목록", "상자", "배경", "버튼", "모양", "글씨", "글자", "숫자", "제목", "영역",
            "가로", "세로", "왼쪽", "오른쪽", "가운데", "위", "아래", "크게", "작게", "큰", "작은",
            "배지", "아이콘", "사진", "이미지", "2열", "3열", "표", "띠", "줄", "선", "무늬", "질감");

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
     * 공통 움직임(block 이 null 인 MOTION)을 쓸 수 있는 블록.
     * hero 는 첫 화면이라 등장 효과가 없다(자기 움직임 — 흩날림 — 이 따로 있다). notices 는 서버 소유다.
     */
    private static final Set<Block> MOTION_BLOCKS =
            EnumSet.of(Block.HIGHLIGHT, Block.INTRO, Block.STATS, Block.BENEFITS, Block.PRIZE, Block.COUPON,
                    Block.COMPARE, Block.AUDIENCE, Block.STEPS, Block.SCHEDULE, Block.FAQ, Block.CTA);

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
     *   벤토(v-*-bento)는 큰 칸 하나 옆에 작은 칸 둘이 서야 격자가 찬다. 2개면 큰 칸 옆이 반쯤 빈다.
     */
    private static final Map<Variant, Integer> NEEDS_ITEMS = Map.of(
            BENEFITS_CAROUSEL, 3,
            STEPS_CAROUSEL, 3,
            PRIZE_PODIUM, 3,
            BENEFITS_BENTO, 3,
            STATS_BENTO, 3,
            PRIZE_BENTO, 3);

    private final Block block;
    private final Group group;
    private final String cssClass;
    private final String desc;
    /** 색 이름이 붙은 변형 — 관리자가 그 색을 말했을 때만 쓴다. 생성 목록에서 뺀다 */
    private final boolean namedColor;
    /** 기본인가 — false 면 확장이다. 요청문이 부를 때만 프롬프트에 실린다 */
    private final boolean core;

    Variant(Block block, Group group, String cssClass, String desc) {
        this(block, group, cssClass, desc, false);
    }

    Variant(Block block, Group group, String cssClass, String desc, boolean namedColor) {
        this(block, group, cssClass, desc, namedColor, true);
    }

    Variant(Block block, Group group, String cssClass, String desc, boolean namedColor, boolean core) {
        this.block = block;
        this.group = group;
        this.cssClass = cssClass;
        this.desc = desc;
        this.namedColor = namedColor;
        this.core = core;
    }

    public Block block()     { return block; }
    public Group group()     { return group; }
    @Override public String cssClass() { return cssClass; }
    @Override public String desc()     { return desc; }
    public boolean namedColor() { return namedColor; }
    @Override public boolean core()    { return core; }

    /** 이 모양이 말이 되려면 있어야 하는 항목 수. 조건이 없으면 0 */
    public int needsItems() {
        return NEEDS_ITEMS.getOrDefault(this, 0);
    }

    /** 그 블록의 전용 모양 — 선언 순서. 첫 번째가 기본이다 (class 를 안 붙인 것과 같다) */
    public static List<Variant> layoutsOf(Block b) {
        return Arrays.stream(values()).filter(v -> v.block == b && v.group == Group.LAYOUT).toList();
    }

    /** 그 블록에 쓸 수 있는 변형 전부 — 전용 배치 먼저, 배경 나중 */
    public static List<Variant> of(Block b) {
        return Arrays.stream(values())
                .filter(v -> v.block == b
                        || (v.block == null && v.group == Group.SURFACE && SURFACE_BLOCKS.contains(b))
                        || (v.block == null && v.group == Group.MOTION && MOTION_BLOCKS.contains(b)))
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
        List<Variant> layouts = dedicatedOf(b).stream().filter(v -> v.core && v.group == Group.LAYOUT).toList();
        return rotate(layouts, b.key() + "|" + (seed == null ? "" : seed), max);
    }

    /** 생성 프롬프트에 실을 배경 변형 — {@link #sample} 과 같은 방식. 색 지정 배경은 뺀다 */
    public static List<Variant> sampleSurfaces(String seed, int max) {
        List<Variant> surfaces = generationSurfaces().stream().filter(v -> v.core).toList();
        return rotate(surfaces, "surface|" + (seed == null ? "" : seed), max);
    }

    /**
     * 생성 프롬프트에 실을 배치 변형 — 표본({@link #sample}) + 요청문이 부른 확장({@link #matched}).
     * ★ 확장은 표본 **뒤에** 붙는다. 확장이 없으면 표본과 똑같다.
     */
    public static List<Variant> forPrompt(Block b, String seed, int max) {
        return Stream.concat(sample(b, seed, max).stream(), matched(b, seed).stream()).toList();
    }

    /** 생성 프롬프트에 실을 배경 변형 — {@link #forPrompt} 와 같은 방식 */
    public static List<Variant> surfacesForPrompt(String seed, int max) {
        return Stream.concat(sampleSurfaces(seed, max).stream(), matchedSurfaces(seed).stream()).toList();
    }

    /**
     * 수정 프롬프트에 실을 변형 — 그 블록의 기본 전부 + 요청문이 부른 확장.
     * ★ 기본은 지금처럼 전부 싣는다 (블록 하나라 짧다). 확장만 요청문으로 거른다.
     * ★ 색 지정 배경({@link #namedColor})은 예외 — 관리자가 <b>그 색을 말했을 때만</b> 싣는다.
     *   안 그러면 "더 화려하게" · "심플하게" 에도 v-surface-blue 가 목록에 있어
     *   모델이 배경색을 임의로 바꿨다(멘토링 제보: 배경·색 말 없이 색이 바뀜).
     */
    public static List<Variant> forEdit(Block b, String requestText) {
        List<Variant> all = of(b);
        return Stream.concat(
                all.stream().filter(v -> v.core && (!v.namedColor || namedColorAsked(v, requestText))),
                KeywordMatch.matched(all, lookText(requestText), MAX_MATCHED, NOT_KEYWORDS, false).stream()).toList();
    }

    /**
     * 이 색 지정 배경의 색을 요청문이 말했는가.
     * ★ {@link ColorWords} 어간과 맞춰 본다 — "파란색으로" 는 SURFACE_BLUE, "그린으로" 는 SURFACE_GREEN.
     *   "배경" 만 말한 요청에는 아무 색도 안 싣는다(임의 선택 구멍이 된다).
     */
    static boolean namedColorAsked(Variant v, String requestText) {
        if (!v.namedColor || requestText == null || requestText.isBlank()) return false;
        for (ColorWords.Group g : ColorWords.GROUPS) {
            boolean inVariant = v.desc.contains(g.name())
                    || g.stems().stream().anyMatch(v.desc::contains);
            if (!inVariant) continue;
            for (String word : g.words()) {
                if (requestText.contains(word)) return true;
            }
        }
        return false;
    }

    /**
     * 요청문이 부른 그 블록 전용 확장 (배치 · 움직임). 많이 맞은 것부터, 최대 {@link #MAX_MATCHED}
     *
     * ★ 페이지 배치를 말한 마디는 빼고 본다 ({@link #lookText}) — "페이지는 벽돌 배치로" 가
     *   혜택 변형 v-benefits-masonry(벽돌)까지 끌어와서 모델이 "선물 포장" 대신 그걸 골랐다 (Bedrock 실측)
     */
    public static List<Variant> matched(Block b, String requestText) {
        return KeywordMatch.matched(dedicatedOf(b), lookText(requestText), MAX_MATCHED, NOT_KEYWORDS, false);
    }

    /** 변형 매칭에 쓸 요청문 — 페이지 배치는 서버가 따로 건다(Layout.requestedBy). 그 마디를 뺀다 */
    static String lookText(String requestText) {
        return Layout.withoutLayoutTalk(requestText);
    }

    /** 요청문이 부른 공통 움직임 (생성용 — 내용 영역 공통) */
    public static List<Variant> matchedMotions(String requestText) {
        List<Variant> motions = Arrays.stream(values()).filter(v -> v.block == null && v.group == Group.MOTION).toList();
        return KeywordMatch.matched(motions, lookText(requestText), MAX_MATCHED, NOT_KEYWORDS, false);
    }

    /** 요청문이 부른 확장 배경 변형 (생성용 — 색 지정 배경은 뺀다) */
    public static List<Variant> matchedSurfaces(String requestText) {
        return KeywordMatch.matched(generationSurfaces(), lookText(requestText), MAX_MATCHED, NOT_KEYWORDS, false);
    }

    /** 그 블록 전용 변형 — 배치 · 움직임. 배경 · 공통 움직임(block 이 null)은 뺀다 */
    private static List<Variant> dedicatedOf(Block b) {
        return Arrays.stream(values()).filter(v -> v.block == b).toList();
    }

    private static List<Variant> generationSurfaces() {
        return Arrays.stream(values()).filter(v -> v.group == Group.SURFACE && !v.namedColor).toList();
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
