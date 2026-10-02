package com.newvent.registry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 블록 레지스트리 — 이 파일 하나가 프롬프트 · 검증기 · CSS 선택자 · 라우터 목록을
 * 전부 만들어낸다. 같은 사실을 여러 파일에 적지 않기 위한 장치다.
 *
 * ★ 고칠 때는 shape 와 must 를 반드시 같이 고친다.
 *   shape 는 "모델에게 시키는 말", must 는 "그게 지켜졌는지 보는 선택자"다.
 *
 * ★ shape 와 must 는 짝이지만 같지는 않다
 *   shape 는 **백지 생성**에서 모델에게 시키는 한 가지 모양이고,
 *   must 는 **그 블록이 성립하는 모든 모양**을 받는다.
 *
 *     shape   "<ul> 안에 <li> 로 항목을 나열한다"     ← 모델에게 시키는 말
 *     must    "ul li, .benefit-card"                  ← 백지도 템플릿도 받는 선택자
 *
 *   템플릿 5종은 benefits 를 <div class="benefit-card"> 로 만든다. <li> 가 하나도 없다.
 *   must 를 "ul li" 로만 두면 템플릿 블록이 전부 empty_benefits 로 떨어진다.
 *   그렇다고 shape 에 "둘 중 아무거나" 라고 쓰면 모델이 헷갈린다 —
 *   **시키는 말은 하나, 받아주는 모양은 여럿**이 맞다.
 */
public enum Block {

    // ★ PERIOD 는 블록이 아니라 슬롯이다 (Slot.PERIOD).
    //   템플릿 5종 전부 기간이 hero 안에 들어가 있어서 독립 섹션으로 뺄 수 없다.
    //   기간 값은 서버가 [data-slot="period"] 를 찾아 채운다.
    //
    // ★ 선언 순서가 곧 문서 순서다. 삽입 자리(EditService.insertBlock)와
    //   유의사항 자리(PageShell.ensureNotices)가 ordinal 로 정해진다.
    //
    // ★ core — 백지 생성에서 **항상** 만드는 블록인가
    //   required 와 다르다. required 는 검증기의 안전망이고(steps 는 false),
    //   core 는 모델에게 "빠뜨리지 마라" 라고 시킬지다(steps 는 true).
    //   core=false 블록은 요청문에 관련 내용이 있을 때만 만들라고 시킨다.
    //   전부 시키면 페이지마다 출력이 두 배가 되고, 없는 내용을 지어낸다.
    // ★ .badge 를 shape 에 넣는다 — event.css 가 hero 안의 .badge 를 테두리 알약으로 그리는데
    //   (그라데이션 배경 위 흰 반투명 테두리) 백엔드가 이 이름을 내보낸 적이 없다.
    //   t-badge 가 아니라 .badge 다. 이건 hero 전용이고, 자리가 제목 위로 정해져 있다.
    HERO("hero", true, true, Source.MIXED,
            "이벤트 제목과 한 줄 소개. 기간은 서버가 넣는다",
            "제목은 <h1>, 소개는 <p> 로 감싼다. 제목 위에 짧은 꼬리말을 둘 수 있다 — "
            + "<span class=\"badge\"> 안에 10글자 이하로 쓴다 (예: 선착순, 신규 가입자 전용)",
            "h1", null, 0, null),

    // ★ <p> 를 두 개까지 쓴다 — event.css 가 둘째를 작고 흐리게 그린다
    //     [data-block="highlight"]:not(.ev-block) p + p
    //       { font-size: 0.92rem; font-weight: 500; opacity: 0.92 }
    //   그동안 shape 가 "<p> 하나" 라고 시켜서 이 자리가 비어 있었다.
    //   안내 상자 변형(v-highlight-alert · info · success · danger)도 같은 규칙을 쓴다 —
    //   "무엇을" 한 줄, "어떤 조건으로" 를 그 아래 작은 줄로 나누면 읽기가 훨씬 낫다.
    //
    // ★ 단, v-highlight-marquee 는 둘째 <p> 를 숨긴다(display:none).
    //   그래서 변형 설명에 "문장 하나만 쓴다" 를 박아 뒀다(Variant.HIGHLIGHT_MARQUEE).
    HIGHLIGHT("highlight", false, false, Source.LLM,
            "핵심 혜택을 한 줄로 강조하는 띠 배너 — 요청문에 있는 혜택으로만 쓴다",
            "<p> 에 강조 문구를 한 문장으로 쓴다. 덧붙일 조건이 있으면 "
            + "<p> 를 하나 더 써서 짧게 적는다 — 둘째 줄은 화면이 작고 흐리게 그린다",
            "p", null, 0, null),

    INTRO("intro", false, false, Source.LLM,
            "이벤트 취지와 배경을 소개하는 짧은 글",
            "<h2> 소제목 하나와 <p> 문단 1~2개로 쓴다",
            "p", null, 0, null),

    // ★ shape 가 카드 **안쪽** class 이름을 말한다 — event.css 에 이미 있는데 안 쓰이고 있었다.
    //   예전 shape 는 "<ul> 안에 <li>" 뿐이었고, 그러면 <li> 안이 맨 텍스트 한 줄이다.
    //   .benefit-icon(54px 알약 아이콘) · .benefit-name · .benefit-desc · .benefit-value ·
    //   .benefit-tag 는 어떤 변형도 건드리지 않아서 백지 페이지에서 그대로 걸린다.
    //   백엔드가 이름을 한 번도 내보내지 않아 쓰이지 않았을 뿐이다.
    //
    // ★ <li> 와 <ul> 에는 class 를 붙이지 않는다 — 카드 **껍데기**는 변형이 이미 만든다.
    //   event.css 의 변형 규칙은 특이도가 높다:
    //     [data-block="benefits"].v-benefits-grid:not(.ev-block) li   → 0,3,1
    //       { padding 18px 16px; border 1px; border-top 4px primary; radius 14px;
    //         background surface; box-shadow md }   ← 이게 이미 카드다
    //     .benefit-card-default                                       → 0,1,0
    //   benefit-card-default 를 붙여도 **변형이 이긴다. 아무 효과가 없다.**
    //   (event.css 주석이 설계를 밝혀 둔다 — "기본 폴백이 :where() 라 여기서 덮을 수 있다".
    //    백지용 기본값만 특이도 0 이고, 변형은 그걸 덮는 쪽이다.)
    //   껍데기는 변형, 안쪽 내용은 템플릿 class — 역할을 갈라 둔다.
    //
    // ★ 꼬리말은 benefit-tag 를 쓴다. t-badge 가 아니다.
    //   t-* 는 문장 **안쪽** 강조 전용이다(t-accent 로 숫자를 강조하는 자리).
    // ★ 아래 네 블록(stats · prize · coupon · schedule)은 event.css 가 먼저 들여왔다.
    //   레지스트리에 이름이 없어서 **쓰이지 못하고 있었다** — 백지 결과가 늘 같은 네 블록
    //   (hero · benefits · steps · cta)으로만 나오던 이유다. 템플릿 5종이 서로 달라 보이는 것도
    //   대부분 이 자리들이다: sports 스코어보드 = stats, 경품 = prize,
    //   vip 쿠폰 = coupon, launch 타임라인 = schedule.
    //
    // ★ 전부 core=false 다. 요청문이 그 내용을 직접 말할 때만 만든다 —
    //   네 개를 다 만들면 페이지가 길어지고, 없는 경품·일정을 지어낸다.

    // 숫자로 보는 혜택. event.css 가 <strong> 을 큰 숫자로 그린다(1.8rem · 900)
    // ★ minItems 가 2 라서 숫자가 하나뿐이면 모델이 **두 번째를 지어낸다.**
    //   실측에서 "지금까지 8,200명" 하나만 줬더니 "1명 · 제주도 여행권 당첨 기회" 를 만들어
    //   숫자 칸을 채웠다. 검증기의 하한이 지어내기를 부른 셈이다.
    //   하한을 1 로 내리면 2열 격자에 카드 하나만 덩그러니 남는다 — 그래서
    //   **"모자라면 아예 만들지 마라" 를 desc 에 박는다.** 안 만들면 아무 일도 안 일어난다.
    STATS("stats", false, false, Source.LLM,
            "숫자로 보는 혜택 — 요청문에 나온 수치만 쓴다. 숫자를 지어내지 마라. "
            + "요청문에 쓸 숫자가 2개 미만이면 이 영역을 만들지 마라",
            "<h2> 소제목과 <ul> 안에 <li> 2개 이상. 각 <li> 는 "
            + "<strong>14,820명</strong> 처럼 숫자를 <strong> 로 먼저 쓰고 "
            + "그 뒤에 무엇의 숫자인지 짧게 붙인다",
            "ul li strong", "ul", 2,
            "(?i)(누적|돌파|달성률|참여자 ?수|명이 참여|지금까지 [0-9])"),

    BENEFITS("benefits", true, true, Source.MIXED,
            "혜택 — 항목은 폼 값, 문장만 다듬는다",
            "<h2> 소제목과 <ul>. <ul> 안에 <li> 로 2개 이상 나열한다. <li> 와 <ul> 에는 class 를 붙이지 않는다. "
            + "<li> 안은 이모지 하나를 담은 <div class=\"benefit-icon\">, "
            + "혜택 이름 <div class=\"benefit-name\">, 설명 한 문장 <div class=\"benefit-desc\">, "
            + "받는 값 <div class=\"benefit-value\"> 순서로 쓴다. "
            + "짧은 꼬리말이 필요하면 <li> 맨 앞에 <span class=\"benefit-tag\">",
            // 백지: ul li · 템플릿: .benefit-card (계약 EVENT_STRUCTURE_CONTRACT §3)
            "ul li, .benefit-card", "ul, .benefits-list", 2, null),

    // 경품. event.css 가 li::before 로 🎁 를, 첫 항목에만 🏆 를 붙인다 — 순서가 곧 등수다
    //
    // ★ "당첨" 을 열쇠말에 넣지 않았다 — 유의사항 문구에 흔해서 오탐이 난다.
    //   "[1-9]등" 은 "3등급" 에 걸리므로 뒤에 "급" 이 오면 뺀다.
    // ★ 이모지를 쓰지 말라고 못 박는다 — steps 번호와 같은 사고였다.
    //   event.css 가 li::before 로 🎁 를, 첫 항목에만 🏆 를 붙인다.
    //   그런데 shape 가 "화면이 트로피를 붙인다" 라고 알려주기만 하고 금지하지 않아서
    //   모델이 본문에 🏆 를 또 써서 **트로피가 두 개 겹쳤다**(여름 수영장 실측).
    //   알려주는 것과 금지하는 것은 다르다.
    PRIZE("prize", false, false, Source.LLM,
            "경품 — 요청문에 나온 경품만 쓴다. 지어내지 마라",
            "<h2> 소제목과 <ul> 안에 <li> 2개 이상으로 경품을 나열한다. "
            + "가장 큰 경품을 맨 앞에 쓴다. "
            + "선물·트로피 이모지를 쓰지 마라 — 화면이 자동으로 붙인다",
            "ul li", "ul", 2,
            "(?i)(경품|추첨|상품권|기프티콘|[1-9]등(?!급))"),

    // 쿠폰 티켓. event.css 가 양옆이 파인 그라데이션 티켓으로 그린다.
    // 다른 블록과 달리 <h2> 가 작은 머리말이고 <p> 가 주인공이다 — shape 에 그대로 적는다
    COUPON("coupon", false, false, Source.LLM,
            "쿠폰 안내 — 요청문에 나온 쿠폰만 쓴다. 금액·비율을 지어내지 마라",
            "<h2> 에 짧은 영문 머리말(예: WELCOME COUPON), "
            + "<p> 에 쿠폰 내용을 한 줄로(예: 전 상품 30% 할인), "
            + "<ul> 안에 <li> 로 사용 조건을 쓴다",
            "p", "ul", 1,
            "(?i)(쿠폰|할인권|바우처|적립금)"),

    // ★ 표는 수치가 몰리는 자리다. "지어내지 마라" 를 역할 설명에 박아 둔다 —
    //   생성 프롬프트와 수정 프롬프트 둘 다 desc 를 읽는다. 수정에서는 ValueCheck 가 한 겹 더 막는다.
    //   container 가 tbody 인 이유: Jsoup 이 <table> 아래에 tbody 를 끼워 넣는다. 행은 거기 달린다.
    //
    // ★ trigger 는 부분 문자열로 찾는다(find). 짧은 말을 넣으면 다른 단어 속에서 걸린다 —
    //   "표를" 은 "발표를", "비교" 는 "비교적" 에 걸렸다. 그러면 요청에 없는 가격표를 지어낸다.
    //   그래서 "비교" 뒤에 붙는 꼴까지 적는다. vs 는 영어 단어 속(canvas)을 피하려고 앞뒤 영문자를 막는다.
    //   오탐 · 정탐 문장은 TriggerAndPlaceholderTest 에 고정했다.
    COMPARE("compare", false, false, Source.LLM,
            "혜택·요금제 비교표 — 요청문에 나온 값만 쓴다. 숫자·가격·용량을 지어내지 마라",
            "<h2> 소제목과 <table> 하나. 첫 행은 <th> 머리글, 나머지 행은 <td>",
            "table tr", "tbody", 2,
            "(?i)(비교 ?표|비교해|비교하는|비교한|요금제 비교|(?<![a-z])vs(?![a-z]))"),

    // ★ "이상" · "대상" 단독은 쓰지 않는다 — "3GB 이상 · 1만원 이상" 에 다 걸린다.
    //   "~ 대상" 꼴과 "대상은/입니다" 꼴로 좁힌다. "가입자가 대상입니다" · "신규 가입자 대상" 은 잡아야 한다.
    AUDIENCE("audience", false, false, Source.LLM,
            "참여 대상 — 요청문에 나온 조건만 쓴다. 조건을 지어내지 마라",
            "<h2> 소제목과 <ul> 안에 <li> 로 대상을 나열한다",
            "ul li", "ul", 1,
            "(참여|가입|가입자|고객|회원|이용자) ?대상|대상자|대상(은|는|이며|입니다|:)|등급 ?고객|고객만|회원만"),

    // ★ 번호를 글자로 적지 않는다. step-badge 를 쓰지 않는다.
    //   steps 변형이 번호를 CSS 로 이미 그린다 —
    //     v-steps-numbered  li::before { content: counter(nv-step) }        → 동그라미 ①
    //     v-steps-timeline  li::before { content: "STEP " counter(nv-step) } → "STEP 1"
    //   여기에 <div class="step-badge">1</div> 를 넣으면 **번호가 두 번 보인다** ("STEP 1" 다음에 "1").
    //   step-card-default 도 안 쓴다 — 변형의 li 규칙(0,3,1)이 이긴다. benefits 와 같은 이유다.
    //
    // ★ 안쪽만 쓴다 — step-title(15px 진하게) · step-desc(13px 흐리게).
    //   둘을 갈라 쓰면 "제목 한 줄 + 설명 한 줄" 이 되고, 변형의 번호가 그 위에 붙는다.
    STEPS("steps", false, true, Source.LLM,
            "참여 방법 2~4단계",
            "<h2> 소제목과 <ol>. <ol> 안에 <li> 로 순서대로 나열한다. <li> 와 <ol> 에는 class 를 붙이지 않는다. "
            + "<li> 안은 두세 단어짜리 제목 <div class=\"step-title\"> 와 "
            + "설명 한 문장 <div class=\"step-desc\"> 두 개만 쓴다. "
            + "단계 번호는 적지 마라 — 화면이 자동으로 붙인다",
            "ol li, .step-card", "ol, .steps-list", 2, null),

    // 일정. event.css 가 세로 타임라인으로 그리고 <strong> 을 때(when)로 강조한다.
    // steps(참여 방법)와 다르다 — steps 는 "무엇을 하는가", schedule 은 "언제 무엇이 열리는가" 다
    SCHEDULE("schedule", false, false, Source.LLM,
            "일정 — 요청문에 나온 날짜만 쓴다. 날짜를 지어내지 마라",
            "<h2> 소제목과 <ol> 안에 <li> 2개 이상. 각 <li> 는 "
            + "<strong>1차 · 10월 1일</strong> 처럼 때를 <strong> 로 먼저 쓰고 설명을 붙인다",
            "ol li strong", "ol", 2,
            "(?i)(일정|차수|[1-9]차 |오픈일|마감일|타임라인|사전 ?예약)"),

    // ★ 모양이 두 벌이다 — 목록형 <dl> 과 펼침형 <details>.
    //   펼침(아코디언)은 class 만으로 못 만든다. 닫힌 <details> 의 내용은 CSS 로 꺼낼 수 없어서
    //   목록형 변형(plain · cards · qa)과 마크업이 갈린다. 그래서 must · container 가 둘 다 받는다.
    //   shape 는 하나만 시킨다(dl). 펼침형은 변형 안내에서 "이걸 고르면 이 마크업" 으로 따로 말한다.
    //
    // ★ "질문" · "궁금" 단독은 쓰지 않는다 — 퀴즈 이벤트("질문에 답하면 경품")와 홍보 문구("궁금하시죠?")에 걸린다.
    FAQ("faq", false, false, Source.LLM,
            "자주 묻는 질문 2~4개 — 페이지에 나온 내용으로만 답한다",
            "<h2> 소제목과 <dl> 안에 질문은 <dt>, 답은 <dd> 로 쓴다",
            "dl dt, details summary", "dl, .ev-accordion", 2,
            "(?i)(자주 묻는|질문과 답|(?<![a-z])faq(?![a-z])|(?<![a-z])q ?& ?a(?![a-z])|문답|궁금한 점)"),

    NOTICES("notices", true, true, Source.SERVER,
            "유의사항 — 승인된 문구만 서버가 삽입",
            null, null, null, 0, null),

    CTA("cta", true, true, Source.MIXED,
            "참여 버튼. 문구만 생성, 링크는 폼 값",
            "<a href=\"#\" class=\"btn\"> 안에 버튼 문구를 넣는다",
            // 템플릿 5종은 전부 <button>. <a> 는 5종 통틀어 0개다.
            // 호스트가 이벤트 위임으로 클릭을 받으므로 button 이 맞는 선택이다.
            "a, button", null, 0, null);

    /** 누가 내용을 만드는가. 프롬프트·클릭 가능 여부·덮어쓰기가 여기서 갈린다. */
    public enum Source {
        /** 모델이 전부 만든다 */
        LLM,
        /** 서버가 채운다. 모델은 만들면 안 된다 */
        SERVER,
        /** 값은 폼·서버, 문장만 모델 */
        MIXED
    }

    private final String key;
    private final boolean required;
    private final boolean core;
    private final Source source;
    private final String desc;
    private final String shape;
    private final String must;
    private final String container;
    private final int minItems;
    private final Pattern trigger;

    Block(String key, boolean required, boolean core, Source source,
          String desc, String shape, String must, String container, int minItems,
          String trigger) {
        this.key = key;
        this.required = required;
        this.core = core;
        this.source = source;
        this.desc = desc;
        this.shape = shape;
        this.must = must;
        this.container = container;
        this.minItems = minItems;
        this.trigger = trigger == null ? null : Pattern.compile(trigger);
    }

    public String key()      { return key; }
    public boolean required(){ return required; }
    /** 백지 생성에서 항상 만드는가. false 면 요청문에 관련 내용이 있을 때만 */
    public boolean core()    { return core; }
    public Source source()   { return source; }
    public String desc()     { return desc; }
    public String shape()    { return shape; }
    public String must()     { return must; }
    public int minItems()    { return minItems; }

    /**
     * 백지 생성에서 이 블록을 만들어도 되는가 — 요청문에 그 내용이 직접 있을 때만.
     *
     * ★ 왜 서버가 거르나 — 프롬프트로 "요청문이 직접 말할 때만" 이라고 해도
     *   FAQ 를 요청하지 않은 이벤트 3건 중 2건에서 질문을 지어냈고, 답이 운영 정책이었다
     *   ("쿠폰은 가입 완료 즉시 지급", Bedrock 실측). 게시되면 지킬 수 없는 약속이 나간다.
     *   요청문에 열쇠말이 없으면 프롬프트 목록에서 빼고(PromptBuilder) 출력에서도 지운다(BlockValidator).
     *
     * ★ trigger 가 없는 블록은 항상 된다. 요청문이 null 이면 거르지 않는다(테스트 · 수정 경로).
     * ★ 수정 경로("FAQ 추가해줘")는 이걸 안 본다 — 관리자가 직접 시킨 것이다.
     */
    public boolean allowedFor(String requestText) {
        return trigger == null || requestText == null || trigger.matcher(requestText).find();
    }

    /**
     * 열쇠말 조건이 달린 블록인가.
     *
     * ★ 프롬프트가 세 묶음으로 갈리는 기준이다 — {@code PromptBuilder.generate}
     *   {@code trigger} 가 있는 블록이 목록에 남아 있다는 건 <b>열쇠말이 이미 걸렸다</b>는 뜻이고
     *   ({@link #llmBlocksFor} 가 걸러서 넣는다), 그러면 "만들지 마라" 가 아니라
     *   "이번엔 만들어라" 가 맞다. 반대로 trigger 가 없는 블록(highlight · intro)은
     *   늘 목록에 있으므로 여전히 모델이 판단한다.
     */
    public boolean hasTrigger() {
        return trigger != null;
    }

    /** 요청문 기준으로 만들어도 되는 모델 블록 */
    public static List<Block> llmBlocksFor(String requestText) {
        return llmBlocks().stream().filter(b -> b.allowedFor(requestText)).collect(Collectors.toList());
    }

    /**
     * 항목이 들어가는 자리. <b>자식 수를 세는 기준이다.</b>
     *
     */
    public String container() { return container; }

    /**
     * 이 블록의 **항목이 관리자가 정하는 값인가.** 지금은 benefits 하나다.
     *
     * ★ 관문(Gate)이 되묻기를 판정하는 근거다
     *
     * ★ if (b == Block.BENEFITS) 로 쓰지 말 것.
     */
    public boolean itemsAreFormValues() {
        return minItems > 0 && source == Source.MIXED;
    }

    /** 모델이 만드는 블록 (SERVER 제외) */
    public static List<Block> llmBlocks() {
        return Arrays.stream(values())
                .filter(b -> b.source != Source.SERVER)
                .collect(Collectors.toList());
    }

    /** 서버가 채우는 블록 — 모델 출력을 무시하고 덮어쓴다 */
    public static List<Block> serverBlocks() {
        return Arrays.stream(values())
                .filter(b -> b.source == Source.SERVER)
                .collect(Collectors.toList());
    }

    /** preview 에서 클릭해 수정 대상으로 고를 수 있는 블록 */
    public static List<Block> clickable() {
        return llmBlocks();
    }

    public static Block of(String key) {
        return find(key).orElseThrow(() -> new IllegalArgumentException("없는 블록: " + key));
    }

    /** 모르는 key 면 빈 값. 모델 출력이나 저장된 HTML 을 볼 때 쓴다 */
    public static Optional<Block> find(String key) {
        if (key == null) return Optional.empty();
        String k = key.trim();
        return Arrays.stream(values()).filter(b -> b.key.equals(k)).findFirst();
    }

    /** CSS 선택자 — event.css 와 검증기가 같은 문자열을 쓰게 한다 */
    public String selector() {
        return "[data-block=\"" + key + "\"]";
    }

    // ── 조작 허용


    /** 채팅으로 내용을 고칠 수 있나 */
    public boolean canEdit() {
        return source != Source.SERVER;
    }

    /** 통째로 지울 수 있나 — 필수 블록은 못 지운다 */
    public boolean canDelete() {
        return !required;
    }

    /** 없을 때 새로 만들 수 있나 */
    public boolean canCreate() {
        return source != Source.SERVER && !required;
    }

    /** 순서를 옮길 수 있나 — 서버 소유라도 위치는 옮겨도 된다 */
    public boolean canMove() {
        return true;
    }

    /** 거부 사유 — 사용자에게 그대로 보여줄 문장 */
    public String denyReason(String op) {
        if (source == Source.SERVER) {
            return switch (op) {

                case "EDIT", "ADD", "STYLE" ->
                        desc + " 영역은 시스템이 관리합니다. 채팅으로 바꿀 수 없습니다.";
                case "DELETE" ->
                        "유의사항은 반드시 표시해야 해서 지울 수 없습니다.";
                default -> null;
            };
        }
        if (required && op.equals("DELETE")) {
            return key + " 영역은 필수라 지울 수 없습니다.";
        }
        return null;
    }

    /**
     * shape 를 시킨 블록은 must 도 있어야 한다.
     * 애플리케이션 시작 시 한 번 부른다. 어긋나면 그 자리에서 죽는 게 낫다.
     */
    public static void assertConsistent() {
        for (Block b : values()) {
            boolean hasShape = b.shape != null && !b.shape.isBlank();
            boolean hasMust  = b.must  != null && !b.must.isBlank();
            if (hasShape != hasMust) {
                throw new IllegalStateException(
                        b.key + ": shape 와 must 는 짝이어야 합니다 " +
                        "(shape=" + hasShape + ", must=" + hasMust + ")");
            }
            if (b.source == Source.SERVER && hasShape) {
                throw new IllegalStateException(
                        b.key + ": SERVER 블록에 shape 를 주면 안 됩니다. 모델이 만들게 됩니다.");
            }
            if (b.minItems > 0 && !hasMust) {
                throw new IllegalStateException(
                        b.key + ": minItems 를 세려면 must 가 필요합니다.");
            }
            // ★ 항목을 세는 블록은 담는 자리가 있어야 한다. 반대도 같다.
            //   한쪽만 있으면 "몇 개인지는 아는데 어디서 세는지 모르는" 상태가 된다.
            boolean hasContainer = b.container != null && !b.container.isBlank();
            if ((b.minItems > 0) != hasContainer) {
                throw new IllegalStateException(
                        b.key + ": minItems 와 container 는 짝이어야 합니다 "
                        + "(minItems=" + b.minItems + ", container=" + hasContainer + ")");
            }
        }
    }
}
