package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 백지 생성 결과가 블록 <b>안쪽</b>까지 채우는지 본다.
 *
 * <h2>무슨 문제를 고친 테스트인가</h2>
 *
 * 예전 {@code shape} 는 "{@code <ul> 안에 <li> 로 항목을 나열한다}" 뿐이었다.
 * 그러면 {@code <li>} 안이 맨 텍스트 한 줄이고, 혜택 카드가 한 줄짜리로 나온다.
 * {@code event.css} 에는 이미 쓸 수 있는 이름이 있는데 백엔드가
 * <b>한 번도 내보내지 않아서</b> 안 쓰이고 있었다 —
 * {@code .benefit-icon}(54px 알약 아이콘) · {@code .benefit-name} · {@code .benefit-desc} ·
 * {@code .benefit-value} · {@code .benefit-tag} · {@code .step-title} · {@code .step-desc} ·
 * hero 의 {@code .badge}.
 *
 * <h2>껍데기는 변형, 안쪽은 템플릿 class — 역할이 갈린다</h2>
 *
 * 카드 <b>껍데기</b>({@code .benefit-card-default} · {@code .step-card-default})는
 * <b>쓰지 않는다.</b> {@code event.css} 의 변형 규칙이 특이도가 훨씬 높아서 못 덮는다.
 *
 * <pre>
 *   [data-block="benefits"].v-benefits-grid:not(.ev-block) li   → 0,3,1
 *     { padding 18px 16px; border-top 4px primary; radius 14px;
 *       background surface; box-shadow md }     ← 이게 이미 카드다
 *   .benefit-card-default                                       → 0,1,0   ← 못 이긴다
 * </pre>
 *
 * {@code event.css} 가 주석으로 설계를 밝혀 둔다 —
 * "기본 폴백(ul li 왼쪽 선)이 {@code :where()} 라 여기서 덮을 수 있다".
 * 백지용 기본값만 특이도 0 이고, 48개 변형이 그걸 덮는 쪽이다.
 * 그래서 <b>껍데기는 변형에 맡기고 안쪽만 채운다.</b>
 *
 * <h2>단계 번호를 글자로 적지 않는 이유</h2>
 *
 * steps 변형이 번호를 CSS 로 그린다 —
 * {@code v-steps-timeline li::before { content: "STEP " counter(nv-step) }},
 * {@code v-steps-numbered li::before { content: counter(nv-step) }}.
 * 여기에 {@code <div class="step-badge">1</div>} 를 넣으면 번호가 두 번 보인다.
 *
 * <h2>이 테스트가 지키는 계약</h2>
 * <ol>
 *   <li>{@code shape} 가 안쪽 class 이름을 담고 있다 — 프롬프트에 그대로 실린다</li>
 *   <li>껍데기 이름과 단계 번호는 <b>프롬프트에 없다</b> — 들어가면 효과가 없거나 번호가 겹친다</li>
 *   <li>정화({@code sanitizeGenerated})가 안쪽 class 를 지우지 않는다</li>
 *   <li>{@code must} · {@code container} · {@code minItems} 가 그대로 성립한다</li>
 *   <li><b>알려진 빈틈</b>: 모델이 지어낸 {@code benefit-*} 이름은 걸러지지 않는다</li>
 * </ol>
 */
class BlankCardMarkupTest {

    private static final String REQ = "데이터 3GB 와 월 요금 30% 할인을 주는 신규 가입 이벤트";

    /**
     * 프롬프트 예시를 그대로 따라 쓴 모델 출력.
     *
     * ★ 이 문자열은 {@code PromptBuilder.generate()} 의 "예:" 와 같은 모양이어야 한다.
     *   예시를 고치면 여기도 고친다 — 보여주는 것과 받아주는 것이 어긋나면
     *   그게 제일 찾기 어려운 버그다.
     */
    private static final String MODEL_OUTPUT = """
            <section data-block="hero" class="v-hero-center palette-summer">
              <span class="badge">선착순</span>
              <h1>여름 데이터 대방출</h1>
              <p>이번 여름, 데이터 걱정 없이 마음껏 즐기세요.</p>
            </section>
            <section data-block="benefits" class="v-benefits-grid v-surface-tint">
              <div class="block-header"><div class="sub-label">Event Benefits</div>
                <h2 class="title">이벤트 혜택</h2></div>
              <ul>
                <li>
                  <div class="benefit-icon">📶</div>
                  <div class="benefit-name">데이터 <strong class="t-accent">3GB</strong> 지급</div>
                  <div class="benefit-desc">가입을 완료하면 바로 사용할 수 있습니다.</div>
                  <div class="benefit-value">즉시 지급</div>
                </li>
                <li>
                  <span class="benefit-tag">6개월 한정</span>
                  <div class="benefit-icon">🎟️</div>
                  <div class="benefit-name">월 요금 할인</div>
                  <div class="benefit-desc">가입 후 6개월 동안 자동으로 적용됩니다.</div>
                  <div class="benefit-value"><span class="t-strike">55,000원</span>
                    <strong class="t-big">38,500</strong>원</div>
                </li>
              </ul>
            </section>
            <section data-block="steps" class="v-steps-timeline">
              <div class="block-header"><div class="sub-label">How to Join</div>
                <h2 class="title">참여 방법</h2></div>
              <ol>
                <li>
                  <div class="step-title">로그인</div>
                  <div class="step-desc">이벤트 페이지에서 로그인합니다.</div>
                </li>
                <li>
                  <div class="step-title">요금제 선택</div>
                  <div class="step-desc">원하는 요금제를 고릅니다.</div>
                </li>
              </ol>
            </section>
            <section data-block="cta" class="v-cta-wide">
              <a href="#" class="btn">참여하기</a>
            </section>
            """;

    private static String cleaned() {
        return BlockValidator.dropUntriggered(
                BlockValidator.sanitizeGenerated(BlockValidator.extract(MODEL_OUTPUT)), REQ);
    }

    // ── ① 프롬프트가 안쪽 이름을 말하는가 ────────────────────────────

    @Nested
    @DisplayName("프롬프트 — 말하는 것")
    class 말하는_것 {

        @Test
        @DisplayName("shape 에 안쪽 class 이름이 들어 있다")
        void shape_가_안쪽_이름을_말한다() {
            assertTrue(Block.BENEFITS.shape().contains("benefit-icon"));
            assertTrue(Block.BENEFITS.shape().contains("benefit-name"));
            assertTrue(Block.BENEFITS.shape().contains("benefit-desc"));
            assertTrue(Block.BENEFITS.shape().contains("benefit-value"));
            assertTrue(Block.BENEFITS.shape().contains("benefit-tag"));
            assertTrue(Block.STEPS.shape().contains("step-title"));
            assertTrue(Block.STEPS.shape().contains("step-desc"));
            assertTrue(Block.HERO.shape().contains("class=\"badge\""),
                    "hero 꼬리말은 t-badge 가 아니라 .badge 다 — "
                    + "event.css 에서 .badge 규칙은 hero 안에 하나뿐이고 어떤 변형도 덮지 않는다");
        }

        @Test
        @DisplayName("생성 프롬프트 예시가 안쪽을 채운 모양이다 — 말보다 예시가 세다")
        void 예시가_안쪽을_채운다() {
            String p = PromptBuilder.generate(REQ);
            assertTrue(p.contains("<div class=\"benefit-icon\">"));
            assertTrue(p.contains("<div class=\"benefit-value\">"));
            assertTrue(p.contains("<div class=\"step-title\">"));
            assertTrue(p.contains("<div class=\"step-desc\">"));
        }

        /**
         * ★ 머리말 묶음은 블록마다 {@code shape} 에 적지 않고 프롬프트 한 줄로 둔다 —
         *   소제목이 있는 블록이 여섯이라 여섯 곳에 베껴 두면 다음에 한 곳만 고치게 된다.
         */
        @Test
        @DisplayName("머리말 묶음과 가격 강조를 시킨다")
        void 머리말과_가격을_시킨다() {
            String p = PromptBuilder.generate(REQ);
            assertTrue(p.contains("class=\"block-header\""));
            assertTrue(p.contains("class=\"sub-label\""));
            assertTrue(p.contains("<h2 class=\"title\">"));
            assertTrue(p.contains("t-strike"), "원가 취소선 용례가 없습니다");
            assertTrue(p.contains("t-big"), "할인가 큰 숫자 용례가 없습니다");
            assertTrue(p.contains("benefit-tag 에 쓴다"), "꼬리말 용례가 없습니다");
        }

        /**
         * ★ 이 줄이 유일한 방어선이다.
         *   {@code benefit-*} 는 허용 목록 검사를 지나가므로(아래 ⑤ 참고)
         *   "지어내지 마라" 를 프롬프트가 말해 주는 것 말고는 막을 방법이 없다.
         */
        @Test
        @DisplayName("이름을 지어내지 말라고 분명히 말한다")
        void 이름을_지어내지_말라고_한다() {
            assertTrue(PromptBuilder.generate(REQ).contains("글자 그대로"));
        }

        /**
         * ★ 열쇠말이 걸린 블록은 "만들어라" 라고 시킨다.
         *
         *   예전에는 둘째 묶음 머리말이 "추측해서 만들지 마라" 하나였고, 그 아래 세 줄도
         *   전부 부정문이었다. 모델이 묶음 전체를 금지로 읽고 <b>경품·쿠폰·일정·숫자를
         *   benefits 카드로 몰아넣었다</b>(여름 수영장 실측).
         *
         *   trigger 가 달린 블록이 목록에 남아 있다는 건 서버가 이미
         *   "요청문이 그 내용을 말한다" 고 판정했다는 뜻이다. 판정 뒤에 금지하면 앞뒤가 안 맞는다.
         */
        @Test
        @DisplayName("★ 열쇠말이 걸린 블록은 '꼭 넣을 영역' 으로 시킨다 — benefits 로 몰리지 않게")
        void 걸린_블록은_만들라고_시킨다() {
            String p = PromptBuilder.generate(
                    "1등 경품은 여행권입니다. 할인 쿠폰도 드립니다. "
                    + "사전예약 1차는 7월 1일입니다. 지금까지 8,200명이 신청했습니다.");

            int must = p.indexOf("이번 요청에 꼭 넣을 영역:");
            assertTrue(must > 0, "열쇠말이 걸렸는데 '꼭 넣을 영역' 묶음이 없습니다");

            // ★ 자리를 나누라는 지시. 한 줄로는 모자랐다 —
            //   실측에서 경품 3개가 benefits 와 prize 에 똑같이 두 번 나왔다.
            assertTrue(p.contains("각 내용은 한 영역에만 쓴다"),
                    "중복을 막는 줄이 없습니다");
            assertTrue(p.contains("benefits 에 다시 쓰지 마라"),
                    "benefits 로 몰리는 것을 막는 줄이 없습니다");
            // 블록 이름이 그 줄에 나열돼야 한다 — 조사를 붙이지 않고 key 만 쓴다
            assertTrue(p.contains("prize · coupon") || p.contains("prize"),
                    "어떤 영역으로 나눠야 하는지 이름이 없습니다");

            // 걸린 네 블록이 그 묶음 안에 있어야 한다 (다음 묶음 전까지)
            int next = p.indexOf("더할 수 있는 영역:");
            String section = p.substring(must, next > must ? next : p.length());
            for (String key : List.of("stats", "prize", "coupon", "schedule")) {
                assertTrue(section.contains("data-block=\"" + key + "\""),
                        key + " 가 '꼭 넣을 영역' 에 없습니다");
            }

            // ★ 서버 판정과 어긋나는 금지는 빠져야 한다
            String noFaq = PromptBuilder.generate("자주 묻는 질문을 넣어주세요");
            assertFalse(noFaq.contains("faq 를 만들지 마라"),
                    "faq 가 목록에 있는데 만들지 말라고 하면 앞뒤가 안 맞습니다");
        }

        @Test
        @DisplayName("혜택 꼬리말은 benefit-tag 라고 못 박는다")
        void 꼬리말은_benefit_tag_다() {
            String p = PromptBuilder.generate(REQ);
            assertTrue(p.contains("benefit-tag 를 쓴다"));
            assertTrue(p.contains("t-badge 를 쓰지 마라"));
        }
    }

    // ── ② 프롬프트가 말하지 "않는" 것 ───────────────────────────────

    /**
     * ★ 여기 있는 것들은 <b>넣으면 안 되는 것</b>이다.
     *   넣어도 컴파일이 되고 검증도 통과하므로, 사람이 "좋아 보여서" 되살리기 쉽다.
     *   왜 안 되는지가 실패 메시지에 들어 있어야 한다.
     */
    @Nested
    @DisplayName("프롬프트 — 말하지 않는 것")
    class 말하지_않는_것 {

        @Test
        @DisplayName("카드 껍데기 class 를 시키지 않는다 — 변형(0,3,1)이 이겨서 효과가 없다")
        void 껍데기_class_를_안_시킨다() {
            String p = PromptBuilder.generate(REQ);
            assertFalse(p.contains("benefit-card-default"),
                    "[data-block=\"benefits\"].v-benefits-grid:not(.ev-block) li 가 0,3,1 이고 "
                    + ".benefit-card-default 는 0,1,0 이다. 붙여도 안 먹는다. "
                    + "변형 li 규칙이 이미 테두리·그림자·여백을 준다");
            assertFalse(p.contains("step-card-default"),
                    "steps 변형 li 규칙도 같은 이유로 이긴다");
            assertFalse(p.contains("benefits-list-default"),
                    "목록 배치는 v-benefits-* 가 맡는다. 같은 특이도(0,1,0)로 다투면 선언 순서에 결과가 달린다");
            assertFalse(p.contains("steps-list-default"));
            assertFalse(Block.BENEFITS.shape().contains("benefit-card-default"));
            assertFalse(Block.STEPS.shape().contains("step-card-default"));
        }

        @Test
        @DisplayName("단계 번호를 글자로 적지 말라고 한다 — CSS 가 이미 그린다")
        void 번호를_적지_말라고_한다() {
            String p = PromptBuilder.generate(REQ);
            assertFalse(p.contains("step-badge"),
                    "v-steps-numbered li::before { content: counter(nv-step) } 가 번호를 그린다. "
                    + "<div class=\"step-badge\">1</div> 를 넣으면 번호가 두 번 보인다");
            assertTrue(p.contains("단계 번호를 글자로 적지 마라"));
            assertFalse(Block.STEPS.shape().contains("step-badge"));
        }

        /**
         * ★ 예시 안에도 번호가 없어야 한다. 모델은 예시를 정답으로 읽으므로
         *   금지 문구보다 예시가 세다 — 둘이 어긋나면 예시가 이긴다.
         */
        @Test
        @DisplayName("예시의 <li> 에 번호 글자가 없다")
        void 예시에_번호가_없다() {
            Document d = Jsoup.parseBodyFragment(cleaned());
            for (Element li : d.select("[data-block=\"steps\"] li")) {
                String t = li.text();
                assertFalse(t.matches("(?s).*\\b(STEP|step)\\s*\\d.*"), "예시에 'STEP 1' 이 있다: " + t);
                assertFalse(t.matches("(?s)^\\s*\\d+\\s*[.단계].*"), "예시에 '1단계' 나 '1.' 이 있다: " + t);
            }
        }
    }

    // ── ③ 정화가 안쪽 class 를 살려 두는가 ──────────────────────────

    @Nested
    @DisplayName("정화")
    class 정화 {

        @Test
        @DisplayName("안쪽 class 가 전부 살아남는다")
        void 안쪽_class_가_남는다() {
            String html = cleaned();
            // ★ class="..." 까지 같이 본다. "badge" 만 찾으면 다른 이름 속에 걸려
            //   hero 의 .badge 가 지워져도 통과한다.
            for (String c : List.of(
                    "benefit-icon", "benefit-name", "benefit-desc", "benefit-value",
                    "benefit-tag", "step-title", "step-desc", "badge", "btn")) {
                assertTrue(html.contains("class=\"" + c + "\""),
                        "class=\"" + c + "\" 가 정화에서 지워졌다");
            }
        }

        @Test
        @DisplayName("<div> 와 이모지가 살아남는다 — Safelist.relaxed() 가 div 를 허용한다")
        void div_와_이모지가_남는다() {
            String html = cleaned();
            assertTrue(html.contains("<div"), "div 가 지워지면 안쪽 구조가 통째로 무너진다");
            assertTrue(html.contains("📶"), "이모지는 benefit-icon 의 내용이다");
        }

        @Test
        @DisplayName("문구 강조(t-accent)는 <strong> 에 붙어 살아남는다")
        void t_accent_가_남는다() {
            assertTrue(cleaned().contains("t-accent"));
        }
    }

    // ── ④ 구조 계약이 그대로 성립하는가 ─────────────────────────────

    @Nested
    @DisplayName("구조 계약")
    class 구조_계약 {

        @Test
        @DisplayName("안쪽을 채워도 검증이 통과한다")
        void 검증이_통과한다() {
            List<BlockValidator.Failure> fs = BlockValidator.validateGenerated(cleaned(), REQ);
            assertTrue(fs.isEmpty(), () -> "실패: " + fs);
        }

        /**
         * ★ 이게 이 변경의 핵심 안전장치다.
         *   {@code <ul><li>} 골격을 그대로 두고 {@code <li>} <b>안쪽</b>만 채웠다.
         *   그래서 {@code must}("ul li, .benefit-card")와
         *   {@code container}("ul, .benefits-list")가 하나도 안 바뀐다.
         */
        @Test
        @DisplayName("must · container · minItems 가 그대로 성립한다")
        void 선택자가_그대로_맞는다() {
            Document d = Jsoup.parseBodyFragment(cleaned());
            for (Block b : List.of(Block.BENEFITS, Block.STEPS)) {
                Element sec = d.selectFirst(b.selector());
                assertNotNull(sec, b.key() + " 섹션이 없다");
                assertFalse(sec.select(b.must()).isEmpty(),
                        b.key() + " must 선택자가 안 맞는다: " + b.must());
                Element box = sec.selectFirst(b.container());
                assertNotNull(box, b.key() + " container 를 못 찾는다: " + b.container());
                assertTrue(box.children().size() >= b.minItems(),
                        b.key() + " 항목이 " + box.children().size() + " 개다");
            }
        }

        @Test
        @DisplayName("<ul> · <ol> · <li> 에는 class 를 붙이지 않는다 — 껍데기는 변형이 맡는다")
        void 목록과_항목에는_class_를_안_붙인다() {
            Document d = Jsoup.parseBodyFragment(cleaned());
            Element ul = d.selectFirst("[data-block=\"benefits\"] ul");
            Element ol = d.selectFirst("[data-block=\"steps\"] ol");
            assertNotNull(ul);
            assertNotNull(ol);
            assertFalse(ul.hasAttr("class"), "목록 배치는 v-benefits-* 가 맡는다");
            assertFalse(ol.hasAttr("class"));
            for (Element li : d.select("[data-block=\"benefits\"] li, [data-block=\"steps\"] li")) {
                assertFalse(li.hasAttr("class"),
                        "<li> 에 붙인 class 는 변형 li 규칙(0,3,1)에 덮인다: " + li.className());
            }
        }

        /**
         * ★ 소제목은 맨 {@code <h2>} 가 아니라 머리말 묶음 안에 들어간다.
         *   {@code .block-header} · {@code .sub-label} · {@code .title} 은 event.css 406행,
         *   <b>{@code :not(.ev-block)} 제약이 없어서</b> 백지 페이지에도 그대로 걸린다.
         *   템플릿 5종이 전부 쓰는 모양인데(Flash Deals · Lucky Fortune Pouch)
         *   백엔드가 이름을 안 내보내서 안 쓰이고 있었다.
         */
        @Test
        @DisplayName("소제목이 block-header 묶음 안에 들어간다")
        void 머리말_묶음이_있다() {
            Document d = Jsoup.parseBodyFragment(cleaned());
            for (String key : List.of("benefits", "steps")) {
                String sel = "[data-block=\"" + key + "\"] .block-header";
                assertNotNull(d.selectFirst(sel + " .sub-label"), key + " 에 영문 머리말이 없습니다");
                assertNotNull(d.selectFirst(sel + " h2.title"), key + " 소제목이 .title 이 아닙니다");
            }
        }

        /**
         * 할인 혜택은 원가에 취소선, 할인가를 크게 — 템플릿 5종이 쓰는 모양이다.
         * {@code t-strike} · {@code t-big} 은 {@code <span>} · {@code <strong>} 이라
         * {@link Inline#sanitize} 를 통과한다.
         */
        @Test
        @DisplayName("가격 강조가 정화를 통과한다 — t-strike · t-big")
        void 가격_강조가_남는다() {
            String html = cleaned();
            assertTrue(html.contains("t-strike"), "원가 취소선이 지워졌습니다");
            assertTrue(html.contains("t-big"), "할인가 큰 숫자가 지워졌습니다");
        }
    }

    // ── ⑤ 알려진 빈틈 ───────────────────────────────────────────────

    /**
     * 모델이 지어낸 {@code benefit-*} 이름은 걸러지지 않는다.
     *
     * ★ 이건 <b>통과하면 안 되는 게 통과하는</b> 테스트가 아니라,
     *   <b>지금 상태를 못 박아 두는</b> 테스트다. 나중에 허용 목록을 걸면
     *   이 테스트가 빨간불이 나고, 그때 기대값을 뒤집으면 된다.
     *
     * ★ 지금 허용 목록을 안 거는 이유
     *   {@code cleanLooks} 는 생성과 수정에 둘 다 걸리고, 수정은 <b>템플릿 블록</b>에도 걸린다.
     *   템플릿 5종이 쓰는 이름을 전부 모으지 않고 목록을 걸면
     *   ({@code sp-*} · {@code hl-*} · {@code vp-*} · {@code fs-*} · {@code lc-*} 계열)
     *   남의 템플릿 디자인을 조용히 지운다. 목록을 먼저 만들어야 하는 일이다.
     */
    @Test
    @DisplayName("★ 알려진 빈틈 — 지어낸 benefit-* 이름은 안 지워진다 (후속 과제)")
    void 지어낸_이름은_안_지워진다() {
        String made = BlockValidator.sanitizeGenerated("""
                <section data-block="benefits"><ul>
                  <li class="benefit-headline v-made-up palette-made-up">
                    <span class="t-made-up">x</span>혜택 하나
                  </li>
                  <li>혜택 둘</li>
                </ul></section>
                """);

        // 허용 목록이 있는 세 접두사는 지워진다
        assertFalse(made.contains("v-made-up"),       "v-* 는 Variant 허용 목록이 거른다");
        assertFalse(made.contains("palette-made-up"), "palette-* 는 Palette 허용 목록이 거른다");
        assertFalse(made.contains("t-made-up"),       "t-* 는 Inline 허용 목록이 거른다");

        // benefit-* 은 접두사가 달라 검사를 지나간다 — CSS 가 없어 효과는 없지만 저장된다
        assertTrue(made.contains("benefit-headline"),
                "이 기대값이 깨지면 허용 목록이 생긴 것이다 — 그때 이 테스트를 뒤집어라");
    }
}
