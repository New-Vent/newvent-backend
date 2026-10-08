package com.newvent.registry;

import java.util.List;
import java.util.StringJoiner;

/**
 * 레지스트리에서 시스템 프롬프트를 만든다.
 *
 * 벤치마크 v8.4 의 build_system() 을 옮긴 것입니다.
 * 이 프롬프트로 S군 140/140, N군(형태 규칙 뺀 것) 0/70 이 나왔습니다.
 *
 * ★ 이모지는 허용으로 바꿨습니다 (벤치마크 v8.4 와 달라진 점)
 *   템플릿 5종 블록 안에 이모지가 58개 있습니다 — 📅 기간 · 🌕 이벤트 기간 처럼
 *   **슬롯 바로 옆에** 붙은 것도 있습니다.
 *   "이모지를 쓰지 마라" 를 주면 모델이 수정할 때 그걸 지우는데,
 *   검증기는 텍스트 변경을 못 잡아서 **요청하지 않은 변경이 조용히 들어갑니다.**
 *
 *   생성 쪽은 문체 규칙이라 통과율에 영향이 적을 것으로 보지만 **측정 전입니다.**
 *   골든 세트 만들 때 전후 비교 항목에 넣어야 합니다.
 */
public final class PromptBuilder {

    private PromptBuilder() {}

    /** 생성 프롬프트에 블록마다 싣는 배치 변형 수 (기본 포함) */
    static final int GENERATE_LAYOUTS_PER_BLOCK = 4;

    /** 생성 프롬프트에 싣는 배경 변형 수 (기본 포함) */
    static final int GENERATE_SURFACES = 4;

    /**
     * 생성과 수정에 **둘 다** 들어가야 하는 금지.
     *
     * ★ 왜 뽑아냈나
     *   이 세 줄이 generate() 에만 있고 edit() 에는 없었습니다.
     *   그런데 "4번째 혜택도 추가해줘" 는 정확히 **수정 경로**입니다.
     *   생성만 막고 수정은 열어두면 막은 의미가 없습니다.
     *   복사해 붙이면 다음에 또 한쪽만 고치게 되므로 한 곳에 둡니다.
     *
     * ★ 혜택은 실제로 지급해야 하는 약속입니다.
     *   모델이 "스타벅스 아메리카노 1잔" 을 지어내고 게시되면
     *   관리자가 모르는 약속이 나간 겁니다. 문구 오류가 아니라 사고입니다.
     *
     * ★ 그리고 검증기는 이걸 못 잡습니다.
     *   자리표시자 검사는 대괄호만 봅니다. 그럴듯하게 쓸수록 안 걸립니다.
     *   프롬프트 · 되묻기 · 폼 값 대조(REQ-LLM-41) 세 겹이 필요하고
     *   이건 그중 **첫 겹**입니다. 이걸로 0% 가 되지 않습니다.
     */
    private static List<String> 지어내기_금지() {
        return List.of(
                "- 날짜를 임의로 만들지 마라. 기간은 주어진 값만 쓴다.",
                "- 주어지지 않은 혜택이나 수치를 만들어내지 마라.",
                "- 실존하는 방송 프로그램, 브랜드, 연예인 이름을 쓰지 마라.");
    }

    /** 생성용 — 페이지 전체를 한 번에 만든다 */
    public static String generate() {
        return generate(null);
    }

    /**
     * 생성용 — 요청문에 맞춰 블록 목록을 거른다.
     *
     * @param requestText 관리자 요청문. 열쇠말이 없는 선택 블록(faq · compare · audience)은
     *                    목록에서 빠진다 — {@link Block#allowedFor}. null 이면 거르지 않는다.
     */
    public static String generate(String requestText) {
        List<Block> blocks = Block.llmBlocksFor(requestText);
        StringJoiner s = new StringJoiner("\n");
        s.add("너는 통신사 이벤트 페이지를 만드는 도우미다.");
        s.add("");
        s.add("출력 규칙:");
        s.add("- 각 영역은 <section data-block=\"이름\"> ... </section> 으로 감싼다.");
        s.add("- <html>, <head>, <body> 태그를 쓰지 마라.");
        s.add("- 코드블록으로 감싸지 마라.");
        s.add("- 설명, 인사말, 마무리 멘트를 붙이지 마라.");
        s.add("");
        s.add("만들 영역: (모두 만든다. 빠뜨리지 마라)");
        for (Block b : blocks) {
            // ★ "(선택)" 을 더 이상 붙이지 않는다 — **모델이 그걸 읽고 실제로 건너뛴다.**
            //   Bedrock 백지 생성에서 steps 가 통째로 빠진 채 나왔고,
            //   required=false 라 검증도 통과해서 참여 방법이 없는 페이지가 저장됐다.
            //   required 는 **검증기의 안전망**이지 모델에게 줄 정보가 아니다.
            //   드물게 정말 못 만드는 경우를 위해 required 자체는 그대로 둔다.
            if (!b.core()) continue;
            addBlockLine(s, b);
        }
        s.add("");
        // ★ 위와 반대로 여기는 건너뛰라고 **시킨다.** 건너뛰는 게 맞는 블록들이다.
        //   전부 만들게 하면 출력이 두 배가 되고, 요청문에 없는 대상·질문을 지어낸다.
        // ★ "관련 내용" 만으로는 약했다 — FAQ 를 요청하지 않은 VIP 이벤트에서 질문 4개를 지어냈고
        //   답이 "별도로 공지됩니다 · 고객센터로 문의" 였다(Bedrock 실측). 직접 요청 기준으로 좁히고
        //   내용 없는 답을 금지 문구로 박는다.
        // ★ 세 묶음으로 가른다. 예전에는 둘이었고, 그게 실측에서 사고가 났다.
        //   둘째 묶음은 머리말과 그 아래 줄이 전부 **부정문**이었다. 모델이 묶음 전체를
        //   금지로 읽고 경품·쿠폰·일정·숫자를 전부 benefits 카드로 몰아넣었다(여름 수영장 실측).
        //
        //   ★ 열쇠말이 걸린 블록이 목록에 남아 있다는 건 **서버가 이미 "요청문이 그 내용을
        //     말한다" 고 판정했다**는 뜻이다(Block.llmBlocksFor). 판정 뒤에 "추측해서 만들지
        //     마라" 라고 하는 건 앞뒤가 안 맞는다. 걸린 것은 만들라고 시킨다.
        List<Block> triggered = blocks.stream()
                .filter(b -> !b.core() && b.requestedBy(requestText)).toList();
        List<Block> optional = blocks.stream()
                .filter(b -> !b.core() && !b.requestedBy(requestText)).toList();

        if (!triggered.isEmpty()) {
            StringJoiner keys = new StringJoiner(" · ");
            for (Block b : triggered) keys.add(b.key());
            s.add("이번 요청에 꼭 넣을 영역: (요청문에 그 내용이 있다. 빠뜨리지 마라)");
            s.add("- " + keys + " 영역을 반드시 만든다. 요청문이 직접 요청했다. 다른 영역에 섞어 넣지 마라.");
            // ★ 단서가 없으면 "반드시" 가 각 영역의 "모자라면 만들지 마라" 를 이긴다.
            //   stats 가 숫자 하나(8,200명)만 있는데 요금제를 세어 "2종" 을 만들어 두 칸을 채웠다
            //   (여름 수영장 2차 실측). 억지로 채우느니 그 영역이 없는 편이 낫다.
            s.add("- 다만 그 영역에 쓸 내용이 요청문에 모자라면 그 영역은 만들지 마라. "
                    + "칸을 채우려고 숫자나 항목을 만들어내지 마라.");
            // ★ 자리를 나누라고 직접 말한다. 한 줄로는 모자랐다 —
            //   실측에서 경품이 benefits 와 prize 에 **똑같이 두 번** 나왔다.
            s.add("- 각 내용은 한 영역에만 쓴다. 같은 것을 두 영역에 쓰지 마라.");
            s.add("- 위 " + keys + " 에 쓴 내용을 benefits 에 다시 쓰지 마라. "
                    + "benefits 에는 그 밖의 혜택만 남긴다.");
            // ★ 되돌려 훔치는 것을 막는다 — 예전 줄("남는 게 없으면 요청문의 다른 혜택을 쓴다")이
            //   쿠폰·경품을 도로 가져오는 허락이 되어 coupon 섹션이 통째로 사라졌다(실측).
            //   금지만 하면 minItems 를 못 채워 재시도가 도니, 쓸 것을 이름으로 정해 준다.
            s.add("- 나눠 쓰고 나서 benefits 에 쓸 게 모자라면 " + keys
                    + " 에서 도로 가져오지 마라. 그 영역이 통째로 사라진다.");
            s.add("- 대신 참여 자체의 이점을 쓴다 — "
                    + "예: 전원 참여 가능, 별도 응모 없이 자동 참여, 당일 바로 사용 가능.");
            for (Block b : triggered) addBlockLine(s, b);
            s.add("");
        }

        s.add("더할 수 있는 영역: (요청문이 그 내용을 직접 말할 때만 만든다. 추측해서 만들지 마라)");
        // ★ 아래 두 줄은 그 블록이 **목록에 없을 때만** 의미가 있다.
        //   목록에 있는데도 "만들지 마라" 라고 하면 서버 판정과 정면으로 어긋난다.
        if (!blocks.contains(Block.FAQ)) {
            s.add("- 요청문에 질문·답이 없으면 faq 를 만들지 마라. '별도 공지', '고객센터 문의' 같은 내용 없는 답은 쓰지 마라.");
        }
        if (!blocks.contains(Block.COMPARE)) {
            s.add("- 요청문에 비교할 값이 없으면 compare 를 만들지 마라.");
        }
        s.add("- 요청문이 주의·유의 사항을 강조해 달라고 하면 highlight 를 만들고 v-highlight-alert 를 고른다.");
        s.add("- 같은 영역(data-block)을 두 번 만들지 마라. highlight 도 하나뿐이다.");
        // ★ 목록에서 빼기만 하면 모델이 이름을 몰라도 비슷한 걸 만든다. 빠진 이름을 직접 말한다.
        //   그래도 만들면 서버가 지운다(BlockValidator.dropUntriggered).
        List<String> skipped = Block.llmBlocks().stream()
                .filter(b -> !blocks.contains(b)).map(Block::key).toList();
        if (!skipped.isEmpty()) {
            s.add("- 이번 요청에서는 " + String.join(", ", skipped) + " 영역을 만들지 마라.");
        }
        for (Block b : optional) addBlockLine(s, b);
        if (!Block.serverBlocks().isEmpty()) {
            s.add("");
            s.add("만들면 안 되는 영역:");
            for (Block b : Block.serverBlocks()) {
                s.add("- data-block=\"" + b.key() + "\" 는 절대 만들지 마라. " + b.desc());
            }
        }
        s.add("");
        s.add("");
        addLooks(s, blocks, requestText);
        s.add("");
        addInline(s);
        s.add("");
        s.add("태그를 반드시 쓴다. 맨 텍스트만 두지 마라.");
        // ★ benefit-* · step-* · badge 는 접두사가 달라서 cleanLooks 의 허용 목록 검사를
        //   통째로 지나간다. 모델이 benefit-headline 같은 걸 지어내면 CSS 가 없어 효과도 없는데
        //   지워지지도 않고 그대로 저장된다. 지금은 프롬프트가 유일한 방어선이다.
        s.add("형태에 적힌 class 이름은 글자 그대로 쓴다. 비슷한 이름을 지어내지 마라. "
                + "목록 밖 이름은 꾸미는 규칙이 없어서 아무 효과도 없다.");
        // ★ .block-header 는 event.css 에서 :not(.ev-block) 제약이 없다 — 백지 페이지에도 걸린다.
        //   소제목이 있는 블록이 여섯이라 shape 여섯 곳에 베끼지 않고 여기 한 줄로 둔다.
        s.add("소제목은 머리말과 함께 쓴다:");
        s.add("  <div class=\"block-header\"><div class=\"sub-label\">영문 한두 단어</div>"
                + "<h2 class=\"title\">한글 소제목</h2></div>");
        s.add("  sub-label 은 그 영역을 한마디로 말하는 영문이다 (예: Event Benefits, How to Join).");
        // ★ 예시에 benefits 와 steps 를 넣는다.
        //   예전 예시는 hero 와 cta 둘뿐이었고, 출력도 딱 그 정도로 나왔다.
        //   라우터에서 이미 겪은 것과 같다 — 예시가 있고 없고가 실제로 갈렸다.
        //   분량을 말로만 시키는 것보다 보여주는 쪽이 세다.
        s.add("예:");
        s.add("<section data-block=\"hero\" class=\"v-hero-center palette-summer\">");
        s.add("  <span class=\"badge\">선착순</span>");
        s.add("  <h1>여름 데이터 대방출</h1>");
        s.add("  <p>이번 여름, 데이터 걱정 없이 마음껏 즐기세요.</p>");
        s.add("</section>");
        s.add("<section data-block=\"benefits\" class=\"v-benefits-grid v-surface-tint\">");
        s.add("  <div class=\"block-header\"><div class=\"sub-label\">Event Benefits</div>");
        s.add("    <h2 class=\"title\">이벤트 혜택</h2></div>");
        s.add("  <ul>");
        s.add("    <li>");
        s.add("      <div class=\"benefit-icon\">\uD83D\uDCF6</div>");
        s.add("      <div class=\"benefit-name\">데이터 <strong class=\"t-accent\">3GB</strong> 지급</div>");
        s.add("      <div class=\"benefit-desc\">가입을 완료하면 바로 사용할 수 있습니다.</div>");
        s.add("      <div class=\"benefit-value\">즉시 지급</div>");
        s.add("    </li>");
        // ★ 둘째 카드는 "할인 혜택" 꼴을 보여준다 — 꼬리말 · 취소선 · 큰 숫자.
        //   말로 "가격을 강조해라" 라고 하는 것보다 한 번 보여주는 쪽이 세다.
        s.add("    <li>");
        s.add("      <span class=\"benefit-tag\">6개월 한정</span>");
        s.add("      <div class=\"benefit-icon\">\uD83C\uDF9F\uFE0F</div>");
        s.add("      <div class=\"benefit-name\">월 요금 할인</div>");
        s.add("      <div class=\"benefit-desc\">가입 후 6개월 동안 자동으로 적용됩니다.</div>");
        s.add("      <div class=\"benefit-value\"><span class=\"t-strike\">55,000원</span> "
                + "<strong class=\"t-big\">38,500</strong>원</div>");
        s.add("    </li>");
        s.add("  </ul>");
        s.add("</section>");
        s.add("<section data-block=\"steps\" class=\"v-steps-timeline\">");
        s.add("  <div class=\"block-header\"><div class=\"sub-label\">How to Join</div>");
        s.add("    <h2 class=\"title\">참여 방법</h2></div>");
        s.add("  <ol>");
        s.add("    <li><div class=\"step-title\">로그인</div>"
                + "<div class=\"step-desc\">이벤트 페이지에서 로그인합니다.</div></li>");
        s.add("    <li><div class=\"step-title\">요금제 선택</div>"
                + "<div class=\"step-desc\">원하는 요금제를 고릅니다.</div></li>");
        s.add("    <li><div class=\"step-title\">신청 완료</div>"
                + "<div class=\"step-desc\">신청하기를 눌러 응모를 마칩니다.</div></li>");
        s.add("  </ol>");
        s.add("</section>");
        s.add("<section data-block=\"cta\" class=\"v-cta-wide\">");
        s.add("  <a href=\"#\" class=\"btn\">참여하기</a>");
        s.add("</section>");
        s.add("");
        // ★ 왜 분량을 시키는가 — **안 시키면 하한에 딱 맞춰 멈춘다 (Bedrock 실측)**
        //   gemma-3-27b 백지 생성 출력이 97 · 109 · 145 토큰이었다.
        //   페이지 전체가 315자, 혜택은 minItems 인 2개 정확히.
        //   모델이 분량을 못 내는 게 아니라 **얼마나 쓸지를 안 알려줬다.**
        //   유일한 압력이 검증기의 하한이었고, 모델은 정확히 거기까지만 썼다.
        s.add("분량:");
        s.add("- 각 영역을 한 줄로 끝내지 마라. 관리자가 그대로 게시할 수 있는 분량으로 쓴다.");
        s.add("- 이벤트 소개는 한두 문장으로 쓴다.");
        // ★ 혜택만 "지어내라" 고 말하지 않는다. 혜택은 실제로 지급해야 하는 약속이고,
        //   없는 걸 만들면 관리자가 모르는 약속이 게시된다. 개수가 아니라 설명을 늘린다.
        // ★ 이 줄이 자리 나누기를 **이겼다**(여름 수영장 2차 실측).
        //   "benefits 에 다시 쓰지 마라" 는 21행에 있고 이 줄은 168행에 있었다.
        //   147줄 뒤에서 "요청문에 있는 것을 빠짐없이" 라고 무조건 말하니
        //   모델이 뒤엣것을 따랐고, 제주도 여행권이 benefits 와 prize 에,
        //   할인 쿠폰이 benefits 와 coupon 에 **각각 두 번** 나왔다.
        //   서로 어긋나는 지시를 두 군데 두면 뒤엣것이 이긴다. 범위를 맞춘다.
        if (triggered.isEmpty()) {
            s.add("- 혜택은 요청문에 있는 것을 빠짐없이 담고, 항목마다 한 문장으로 설명을 붙인다.");
        } else {
            StringJoiner tk = new StringJoiner(" · ");
            for (Block b : triggered) tk.add(b.key());
            s.add("- 혜택은 " + tk + " 로 보낸 것을 뺀 나머지만 담고, 항목마다 한 문장으로 설명을 붙인다.");
        }
        // ★ 칸을 비우면 템플릿보다 **더** 허전해 보인다 — benefit-icon 이 비면
        //   연한 배경의 54px 빈 사각형만 남는다. 구조를 시켰으면 채우기도 시켜야 한다.
        s.add("- 혜택 항목의 칸을 비워 두지 마라. 이모지 · 이름 · 설명 · 받는 값을 모두 채운다.");
        s.add("- 받는 값은 짧게 쓴다 (예: 3GB, 30% 할인, 6개월, 즉시 지급). 문장을 넣지 마라.");
        s.add("- 요청문에 할인 전후 가격이 있으면 받는 값에 "
                + "<span class=\"t-strike\">원래 가격</span> 과 <strong class=\"t-big\">할인가</strong> 를 같이 쓴다.");
        s.add("- 할인율 · 수량 한정 · 등수 같은 짧은 꼬리말은 benefit-tag 에 쓴다 (예: 78% OFF, 선착순 100명).");
        //   반대로 참여 방법은 모델이 만드는 영역이다(레지스트리 Source.LLM).
        //   절차를 지어내는 것은 허용이고, 그래서 요청문에 없어도 만들라고 시킨다.
        s.add("- 참여 방법은 3단계로 쓴다. 요청문에 절차가 없으면 일반적인 온라인 응모 절차로 쓴다.");
        // ★ step-title 과 step-desc 는 글씨 크기·색이 다르다(15px 진하게 / 13px 흐리게).
        //   둘에 같은 문장을 넣으면 같은 말이 두 번 보인다. 역할을 갈라서 시킨다.
        s.add("- 참여 방법은 단계마다 제목과 설명을 따로 쓴다. 제목은 두세 단어, 설명은 한 문장이다.");
        // ★ 번호를 쓰게 하면 화면에 두 번 보인다. 변형이 li::before 로 이미 그린다.
        s.add("- 단계 번호를 글자로 적지 마라. '1단계', '1.', 'STEP 1' 을 쓰지 마라. 화면이 자동으로 붙인다.");
        s.add("");
        // ★ 왜 문체를 시키는가 — 회차마다 흔들린다 (Bedrock 실측)
        //   같은 요청문에 "즐기세요!" 와 "즐겨봐!" 가 번갈아 나왔다.
        //   통신사 이벤트 페이지에 반말이 나가면 그대로 사고다.
        //   검증기는 문체를 못 본다. 프롬프트가 유일한 방어선이다.
        s.add("문체:");
        s.add("- 존댓말로 쓴다. 반말을 쓰지 마라.");
        s.add("- 과장하지 마라. 확인되지 않은 최상급 표현을 쓰지 마라.");
        s.add("");
        s.add("금지:");
        for (String line : 지어내기_금지()) s.add(line);
        s.add("- 대괄호 자리표시자를 절대 남기지 마라. 값을 모르면 그 문장을 아예 빼라.");
        // ★ "이모지를 쓰지 마라" 를 뺐다. 템플릿이 이모지를 쓰므로 톤을 맞춘다.
        //   적극적으로 쓰라고 시키지도 않는다 — 금지를 뺀 것과 권장하는 것은
        //   출력에 미치는 영향이 다르고, 후자는 측정 없이 넣을 이유가 없다.
        //
        // ★ data-slot 은 여기서 언급하지 않는다.
        //   생성은 빈 문서에서 만드는 것이라 슬롯이 있을 이유가 없다.
        //   "만들지 마라" 라고 알려주는 것보다 개념 자체를 모르는 게 확실하다.
        //   혹시 만들어도 sanitizeGenerated() 가 지운다.
        return s.toString();
    }

    /** 수정용 — 백지 생성에서 온 블록 기준 (모양 변형을 안내한다) */
    public static String edit(Block b) {
        return edit(b, false);
    }

    /**
     * 수정용 — 블록 하나만 주고 하나만 받는다.
     *
     * @param templateBlock 템플릿에서 온 블록인가(.ev-block). 그러면 모양 변형을 안내하지 않는다 —
     *                      event.css 의 변형 규칙이 템플릿 블록에는 안 걸려서 골라도 안 바뀐다.
     *                      팔레트는 페이지 루트에 걸리므로 템플릿이어도 안내한다.
     */
    public static String edit(Block b, boolean templateBlock) {
        return edit(b, templateBlock, true);
    }

    /**
     * @param allowPalette hero 에 페이지 전체 색감(팔레트)을 안내할까.
     *                     ★ 영역을 골라 고칠 때는 false — 팔레트는 저장 직전 페이지 루트로 옮겨져(PageShell.hoistPalette)
     *                       고르지 않은 영역의 색까지 바뀐다. 선택 영역 수정이 선택 밖을 건드리면 안 된다
     */
    public static String edit(Block b, boolean templateBlock, boolean allowPalette) {
        return edit(b, templateBlock, allowPalette, false);
    }

    /**
     * @param looksRequest 라우터가 이 연산을 STYLE(겉모양)로 분류했나.
     *
     * ★★ 왜 생겼나 — 라우터가 고른 op 이 **모델에게 안 가고 있었다.**
     *   EditService 는 step.op() 을 DELETE 지름길과 로그 한 줄에만 쓰고,
     *   프롬프트는 EDIT 이든 STYLE 이든 **글자 하나 다르지 않았다.**
     *   그 결과 "제목을 더 화려하게" 에 모델이 이모지와 <strong> 만 붙이고
     *   레이아웃은 오히려 v-hero-split → 기본 가운데 정렬로 밋밋해졌다(실제 화면).
     *
     *   아래 '모양 고르기' 가 "모양을 바꿔 달라는 요청일 때만" 이라는 **모델이 스스로
     *   판단해야 하는 조건문** 뒤에 있는 게 원인이다. 작은 모델은 그 판단에서 늘 지고,
     *   더 쉬운 길(문구 고치기)로 샌다. 라우터는 이미 STYLE 로 answer 를 갖고 있으니
     *   그 답을 넘겨 조건문을 **단정문**으로 바꾼다.
     */
    public static String edit(Block b, boolean templateBlock, boolean allowPalette, boolean looksRequest) {
        if (b.source() == Block.Source.SERVER) {
            throw new IllegalArgumentException(
                    b.key() + " 는 서버 소유입니다. 모델에게 수정시키면 안 됩니다.");
        }
        // ★ 모양·색 요청의 길. 고를 게 없으면 관련 문장을 통째로 뺀다 —
        //   "모양 고르기" 를 언급만 하고 목록이 없으면 모델이 목록 밖 이름을 지어낸다.
        List<Variant> variants = templateBlock ? List.of() : Variant.of(b);
        boolean palette = b == Block.HERO && allowPalette;
        // ★★ 테마(basic · bloom · aurora)는 수정 프롬프트에 **한 번도 안 나왔다.**
        //   "더 화려하게" 의 가장 큰 지렛대가 통째로 없었던 셈이다 — 변형은 영역 하나의
        //   배치만 바꾸지만 테마는 페이지 전체의 꼴을 바꾼다.
        // ★ 배관은 이미 다 깔려 있다. 모델이 hero 의 class 에 붙이면
        //   BlockValidator.cleanLooks 가 hero 의 것 하나만 남기고(isRoot 가드),
        //   저장 직전 PageShell.settle → hoist 가 루트로 옮긴다. 팔레트와 똑같은 길이다.
        // ★ 템플릿에는 안내하지 않는다. 템플릿 페이지의 루트에는 theme-sports 처럼
        //   **다른 벌**이 붙어 있어서, blankThemes 를 고르면 hoist 가 그걸 갈아끼워
        //   템플릿 디자인이 통째로 깨진다.
        boolean themes = palette && !templateBlock;
        boolean looks = !variants.isEmpty() || palette || themes;

        StringJoiner s = new StringJoiner("\n");
        s.add("너는 이벤트 페이지의 영역 하나를 수정하는 도우미다.");
        s.add("");
        s.add("<section data-block=\"" + b.key() + "\"> 영역만 수정해서 그 영역만 출력한다.");
        s.add("이 영역의 역할: " + b.desc());
        // ★ 같은 말을 앞과 뒤에 두 번 둔다. 작은 모델은 가운데를 흘린다
        if (looksRequest && looks) {
            s.add("이번 요청은 **겉모습**을 바꾸는 요청이다. 아래 '모양 고르기' 를 반드시 쓴다.");
        }
        // ★ shape 는 백지 생성에서 시킨다. 수정에서는 주지 않는다.
        //   템플릿 benefits 는 .benefit-card div 인데 "<ul> 안에 <li>" 를 주면
        //   모델이 구조를 갈아엎어서 디자인이 망가진다.
        s.add("");
        s.add("출력 규칙:");
        s.add("- <section data-block=\"" + b.key() + "\"> 로 시작해서 </section> 으로 끝난다.");
        s.add("- 다른 영역을 새로 만들지 마라.");
        s.add("- 코드블록으로 감싸지 마라.");
        s.add("- 설명을 붙이지 마라.");
        s.add("");
        // ★ 생성과 반대다. 생성에서는 슬롯을 숨기고, 수정에서는 지키라고 말한다.
        //   수정 대상 HTML 에는 이미 서버가 심은 data-slot 이 들어 있고,
        //   이게 지워지면 그 자리에 값을 채울 방법이 영영 없어진다(merge 가 통째로 갈아끼우므로).
        s.add("건드리면 안 되는 것:");
        s.add("- data-slot=\"...\" 이 붙은 태그는 지우지 마라. 태그와 속성을 그대로 둔다.");
        s.add("- 그 안의 내용을 채우지 마라. 비어 있으면 비운 채로 둔다. 서버가 채운다.");
        s.add("- data-slot 을 새로 만들지 마라.");
        s.add("- class 를 바꾸거나 지우지 마라. 디자인과 버튼 동작이 class 에 걸려 있다."
                + (looks ? " 단, 아래 '모양 고르기' 에 있는 class 는 바꿀 수 있다." : ""));
        s.add("- id 를 지우거나 새로 만들지 마라. 화면 기능이 id 로 요소를 찾는다.");
        s.add("- <button> 을 <a> 나 <div> 로 바꾸지 마라.");
        // ★ 이름 목록은 싣지 않는다 — 모델이 behavior 를 고르게 하면 안 된다. 있는 것을 두라고만 한다
        s.add("- data-behavior 속성과 data-target 같은 동작 속성은 지우거나 바꾸지 마라. 새로 만들지도 마라.");
        // ★ 이모지 보존을 명시한다. 템플릿 블록 안에 58개가 있고,
        //   그중 일부는 슬롯 옆에 붙어 있다(📅 기간).
        //   지우는 것은 요청받지 않은 변경이고 검증기가 못 잡는다.
        s.add("- 원래 있던 이모지를 지우지 마라. 문구를 바꿀 때도 그대로 둔다.");
        s.add("");
        s.add("금지:");
        s.add("- 요청받은 것만 바꿔라. href, class 같은 기존 속성은 그대로 둔다"
                + (looks ? " (모양 고르기의 class 만 예외)." : "."));
        s.add("- href=\"#\" 는 그대로 둬라. 실제 주소를 만들어 넣지 마라.");
        for (String line : 지어내기_금지()) s.add(line);
        s.add("- 대괄호 자리표시자를 남기지 마라.");
        // ★ 항목이 관리자가 정하는 값인 블록에만 한 줄 더 붙인다.
        //   benefits 는 "혜택 3개" 가 실제로 지급해야 하는 약속이라, 모델이 4번째를
        //   지어내면 관리자가 모르는 약속이 게시된다. 문구 오류가 아니라 사고다.
        //   steps 처럼 모델이 쓰는 블록에는 붙이지 않는다 — 붙이면 다듬기도 막힌다.
        //
        //   ★ if (b == Block.BENEFITS) 로 쓰지 말 것. 레지스트리를 만든 의미가 없어진다.
        if (b.itemsAreFormValues()) {
            s.add("- 항목을 새로 만들거나 지우지 마라. 개수는 그대로 두고 문장만 다듬는다.");
        }

        s.add("");
        addInline(s);

        if (looks) {
            s.add("");
            if (looksRequest) {
                // ★★ 단정문이다. "…일 때만" 을 쓰면 모델이 해당 여부를 스스로 판단하다가
                //   더 쉬운 길(문구 고치기)로 샌다 — 그게 이 버그였다.
                s.add("모양 고르기: **이번 요청이 이것이다. 반드시 하나를 골라 바꾼다.**");
                s.add("- class 를 안 바꾸면 화면은 하나도 안 바뀐다. 문구만 고치고 끝내지 마라.");
                s.add("- 지금 <section> 에 붙어 있는 이름과 **다른** 이름을 고른다.");
                s.add("- 문장의 뜻과 숫자는 그대로 둔다. 바꾸는 것은 겉모습이다.");
                s.add("- 위 '문구 꾸밈' 의 이름도 같이 더 붙여 강약을 준다.");
            } else {
                s.add("모양 고르기: (모양·색·분위기를 바꿔 달라는 요청일 때만. 문구는 그대로 둔다)");
            }
            s.add("- <section> 의 class 에 아래 이름을 붙이거나 다른 이름으로 바꾼다. 목록에 없는 이름은 쓰지 마라.");
            addVariantLines(s, variants);
            // ★ 색을 콕 집어 말하면 그 색 이름의 배경을 고르라고 못 박는다.
            //   안 그러면 "파란색으로" 에 어두운 배경 · 그림자 같은 엉뚱한 걸 고르고 성공으로 끝난다
            if (variants.stream().anyMatch(Variant::namedColor)) {
                s.add("- 특정 색(파란색 · 초록색 …)으로 바꿔 달라면 배경에서 그 색 이름이 적힌 것을 고른다.");
            }
            // ★★ 영역을 골라 고치면 팔레트가 빠진다(allowPalette=false). hero 는 배경 변형
            //   대상도 아니라 v-surface-보라 같은 것도 없다. 그런데 '문구 꾸밈' 에는
            //   t-badge-purple 처럼 **색 이름이 적힌** 항목이 있다.
            //   그래서 "보라색으로" 요청에 프롬프트 전체에서 '보라' 가 적힌 유일한 것
            //   (t-badge-purple)이 걸렸고, 모델이 VIP · 고객님 같은 단어에 보라 알약을
            //   붙였다 — 페이지 색은 그대로인데 "v6 로 반영했어요" 가 나갔다(실제 화면).
            //   모델이 틀린 게 아니라 **고를 게 그것뿐이었다.** 길을 막아 준다.
            //
            // ★ 둘째 줄이 중요하다. 아무것도 안 바꾸면 EditService 의 "조용한 실패" 검사가
            //   걸려 cannotDo() 가 "영역 선택을 해제하고 '전체 색감을 보라색으로' 라고
            //   말씀해 주세요" 를 관리자에게 보낸다. 틀린 성공보다 정직한 실패가 낫다.
            if (!palette) {
                s.add("- 이 영역에서는 페이지 색을 바꿀 수 없다. 색을 바꿔 달라는 요청이 와도 "
                        + "문구 꾸밈(t-badge-* · t-accent)으로 대신하지 마라. "
                        + "그건 단어 하나를 강조하는 자리지 색을 바꾸는 자리가 아니다.");
                s.add("- 색만 바꿔 달라는 요청이고 바꿀 배치가 마땅치 않으면 아무것도 바꾸지 마라. "
                        + "서버가 관리자에게 색 바꾸는 방법을 따로 안내한다.");
            }
            // ★ 생성(addLooks)과 같은 순서다 — 구조(테마)를 먼저 정하고 색(팔레트)을 얹는다
            if (themes) {
                s.add("- 페이지 전체 분위기: 아래 중 하나를 <section> 의 class 에 붙인다 (하나만). 페이지 전체에 적용된다.");
                for (Theme t : Theme.blankThemes()) {
                    s.add("    " + t.cssClass() + " : " + t.desc());
                }
            }
            if (palette) {
                s.add("- 페이지 전체 색감: 아래 중 하나 (하나만). 페이지 전체에 적용된다.");
                // ★★ 이 줄이 없어서 "전체 색감을 보라색으로" 가 통째로 실패했다(실제 화면).
                //   팔레트 **이름**은 전부 분위기다 — spring · summer · autumn · lavender.
                //   색 이름이 붙은 팔레트는 하나도 없다. 그래서 "보라색" 요청에 모델이
                //   palette-purple 을 찾다가 못 찾고 **아무것도 안 골랐다.**
                //   정작 설명에는 보라가 셋이나 있다 — sunset(코랄, 보라) · pastel(연보라, 민트) ·
                //   lavender(연보라, 차분한). 이름이 아니라 **설명**을 보라고 말해 주면 된다.
                // ★ 기존 "배경에서 그 색 이름이…" 줄과 다르다. 그건 v-surface-* 용이고
                //   hero 는 배경 변형 대상이 아니라 아예 안 나간다.
                s.add("  ★ 이름은 분위기(봄 · 여름 · 라벤더)이고, 색은 **설명**에 적혀 있다. "
                        + "특정 색(보라색 · 파란색 …)으로 바꿔 달라면 그 색 이름이 **설명에 적힌** 것을 고른다.");
                for (Palette p : Palette.all()) {
                    s.add("    " + p.cssClass() + " : " + p.desc());
                }
            }
        }
        return s.toString();
    }

    private static void addBlockLine(StringJoiner s, Block b) {
        s.add("- data-block=\"" + b.key() + "\" : " + b.desc());
        if (b.shape() != null) {
            s.add("    형태: " + b.shape());
        }
    }

    /**
     * 생성용 모양 안내 — 블록마다 고를 수 있는 변형과 팔레트.
     *
     * ★ "다양하게 섞어라" 를 명시한다. 안 그러면 예시에 나온 것만 매번 고른다
     *   (라우터 · 분량에서 이미 겪었다 — 모델은 예시를 정답으로 읽는다).
     */
    private static void addLooks(StringJoiner s, List<Block> blocks, String seed) {
        s.add("모양 고르기:");
        s.add("- 각 <section> 의 class 에 아래 이름을 붙여 모양을 고른다. 묶음마다 하나씩만.");
        s.add("- 요청문의 분위기에 맞게 고르고, 영역마다 다른 모양을 섞어 단조롭지 않게 한다.");
        s.add("- 목록에 없는 class 이름은 쓰지 마라.");
        s.add("- 영역마다 아래에서 하나를 골라 붙인다. 붙이지 않으면 평범한 기본 모양이 된다.");
        // ★ 딱 맞는 이름이 없을 때 모델이 이름을 **지어낸다.** 지어낸 이름은 cleanLooks 가 지우고,
        //   화면은 그대로인데 "반영했어요" 가 나간다 — 조용한 실패다.
        s.add("- 요청 분위기에 딱 맞는 이름이 없으면, 목록에서 분위기가 가장 가까운 것을 고른다. 새 이름을 만들지 마라.");
        // ★ 전부 싣지 않는다 — 변형이 100개를 넘어 다 실으면 입력이 두 배가 된다.
        //   블록마다 기본을 뺀 몇 개만 싣는다(Variant.sample). 같은 요청문이면 같은 목록이라
        //   재시도가 흔들리지 않고, 요청문이 다르면 다른 목록이라 페이지마다 모양이 갈린다.
        for (Block b : blocks) {
            List<Variant> vs = Variant.sample(b, seed, GENERATE_LAYOUTS_PER_BLOCK);
            if (vs.isEmpty()) continue;
            // ★ 대괄호로 묶지 않는다. 검증기가 [..] 를 자리표시자로 본다(placeholder).
            //   "[hero]" 로 썼더니 모델이 그 표기를 출력에 따라 써서 첫 시도의 2/3 가 떨어졌다(Bedrock 실측).
            s.add("  " + b.key() + " 영역:");
            addVariantLines(s, vs);
        }
        List<Variant> surfaces = Variant.sampleSurfaces(seed, GENERATE_SURFACES);
        StringJoiner line = new StringJoiner(" | ");
        for (Variant v : surfaces) line.add(v.cssClass() + " (" + v.desc() + ")");
        s.add("  배경 (hero · highlight · coupon · cta 를 뺀 내용 영역에 하나씩): " + line);
        // ★ 테마를 색감보다 **먼저** 보여 준다 — 구조를 정하고 색을 얹는 순서다.
        //   금지문을 쓰지 않는다. v-benefits-carousel 은 프롬프트 전체에서
        //   "고르지 마라" 문장에만 나왔고, 모델이 거기서 이름을 배워 골랐다.
        //   대신 BASIC 의 desc 가 "분위기가 분명하지 않으면 이것" 이라는
        //   **긍정형 출구**를 준다. 목록을 벗어난 이름은 BlockValidator 가 지운다.
        s.add("- 페이지 분위기: hero 의 class 에 아래 중 하나를 붙인다 (하나만).");
        for (Theme t : Theme.blankThemes()) {
            s.add("    " + t.cssClass() + " : " + t.desc());
        }
        s.add("- 페이지 전체 색감: hero 의 class 에 아래 중 하나를 붙인다 (하나만).");
        for (Palette p : Palette.all()) {
            if (p == Palette.BASE) continue;     // 생성에는 되돌릴 원래 색이 없다
            s.add("    " + p.cssClass() + " : " + p.desc());
        }
    }

    /**
     * 문구 꾸밈 안내 — 생성 · 수정 공통.
     *
     * ★ "아껴 써라" 를 붙인다. 안 붙이면 문장마다 배지를 단다 —
     *   배지는 드물어야 눈에 띈다. 개수는 검증기가 안 세므로 프롬프트가 유일한 압력이다.
     */
    private static void addInline(StringJoiner s) {
        s.add("문구 꾸밈: (꼭 필요한 단어에만, 영역마다 1~2개까지)");
        s.add("- 배지(t-badge)는 " + Inline.BADGE_MAX + "글자 이하의 짧은 말에만 붙인다 (예: 한정, NEW, 무료). 문장에 붙이지 마라.");
        // ★ 혜택 <li> 바로 안의 꼬리말은 t-badge 가 아니라 benefit-tag 다.
        //   t-badge 는 문장 안에 끼우는 작은 배지이고, benefit-tag 는 카드 왼쪽 위에 놓이는
        //   꼬리말 자리다(연한 primary 배경 알약). 자리가 다르니 이름도 다르다.
        //   ★ 병합 해결에서 이 줄을 빠뜨려 BlankCardMarkupTest 가 깨졌다.
        s.add("- 단, 혜택 <li> 바로 안의 꼬리말은 benefit-tag 를 쓴다. t-badge 를 쓰지 마라.");
        s.add("- <span> · <mark> · <strong> 의 class 에 아래 이름을 붙인다. 목록에 없는 이름은 쓰지 마라.");
        s.add("  예) <span class=\"t-badge t-badge-red\">한정</span> · <strong class=\"t-accent\">3GB</strong>");
        for (Inline i : Inline.values()) {
            s.add("    " + i.cssClass() + " : " + i.desc());
        }
        s.add("- 구분선이 필요하면 <hr> 를 쓴다.");
    }

    private static void addVariantLines(StringJoiner s, List<Variant> vs) {
        for (Variant.Group g : Variant.Group.values()) {
            List<Variant> in = vs.stream().filter(v -> v.group() == g).toList();
            if (in.isEmpty()) continue;
            StringJoiner line = new StringJoiner(" | ");
            for (Variant v : in) line.add(v.cssClass() + " (" + v.desc() + ")");
            s.add("    " + (g == Variant.Group.LAYOUT ? "배치" : "배경") + ": " + line);
        }
    }

    public static String router() {
        StringJoiner names = new StringJoiner(", ");
        for (Block b : Block.values()) names.add(b.key());

        StringJoiner s = new StringJoiner("\n");
        s.add("너는 사용자 요청을 분류하는 라우터다. JSON 하나만 출력한다.");
        s.add("");
        s.add("영역 이름: " + names);
        s.add("");
        s.add("출력 형식:");
        s.add("{\"ops\":[{\"op\":\"<동작>\",\"target\":\"<영역 이름>\",\"content\":<사용자가 쓴 문구 또는 null>}]}");
        s.add("");
        s.add("동작:");
        s.add("- \"EDIT\"    특정 영역의 내용을 고친다");
        s.add("- \"ADD\"     없는 영역을 새로 넣는다");
        s.add("- \"DELETE\"  영역을 통째로 지운다");
        // ★ "겉모양만" 이라고만 쓰면 모델이 색·굵기로 좁게 읽는다. "더 화려하게" 는
        //   레이아웃·분위기 요청인데 EDIT 으로 떨어져 문구만 고쳐졌다(실제 화면).
        s.add("- \"STYLE\"   겉모양을 바꾼다 — 색·크기·굵기, 그리고 더 화려하게·눈에 띄게·고급스럽게 같은 분위기 요청");
        s.add("");
        s.add("content 규칙:");
        s.add("- 사용자가 **직접 쓴 문구**가 있으면 그 문구만 그대로 넣는다.");
        s.add("- 없으면 null 이다. 지어내지 마라. 요청문 전체를 넣지 마라.");
        s.add("");
        // ★ 예시가 있고 없고가 실제로 갈렸다 — v11 1차에서 모델이 요청문을 통째로
        //   content 에 넣는 일이 4건 났고, 아래 예시를 넣자 0건이 됐다.
        //   줄여도 되는 줄이 아니다.
        s.add("예시:");
        s.add("\"제목을 가을 대축제로 바꿔줘\"");
        s.add("{\"ops\":[{\"op\":\"EDIT\",\"target\":\"hero\",\"content\":\"가을 대축제\"}]}");
        s.add("");
        s.add("\"혜택에 데이터 10GB 증정을 추가해줘\"");
        s.add("{\"ops\":[{\"op\":\"ADD\",\"target\":\"benefits\",\"content\":\"데이터 10GB 증정\"}]}");
        s.add("");
        s.add("\"혜택 하나 더 추가해줘\"");
        s.add("{\"ops\":[{\"op\":\"ADD\",\"target\":\"benefits\",\"content\":null}]}");
        s.add("");
        // ★ 페이지 전체 색감은 hero 의 STYLE 로 보낸다. 팔레트를 고르는 자리가 hero 다
        //   (Palette · PageShell.hoistPalette). 이 예시가 없으면 "알 수 없는 영역" 으로 떨어진다.
        s.add("\"전체 색감을 가을 느낌으로 바꿔줘\"");
        s.add("{\"ops\":[{\"op\":\"STYLE\",\"target\":\"hero\",\"content\":null}]}");
        s.add("");
        s.add("\"제목 바꾸고 참여방법도 더 친절하게 다듬어줘\"");
        s.add("{\"ops\":[{\"op\":\"EDIT\",\"target\":\"hero\",\"content\":null},"
                + "{\"op\":\"EDIT\",\"target\":\"steps\",\"content\":null}]}");
        s.add("");
        // ★★ 바로 위 예시와 **짝**이다. 문장 꼴이 거의 같고(…도 더 ~하게) op 만 갈린다 —
        //   "친절하게 다듬어줘" 는 문구라 EDIT, "화려하게" 는 겉모양이라 STYLE.
        //   이 짝이 없을 때 "제목·소개 참여 방법 더 화려하게" 가 위 예시를 그대로 베껴
        //   EDIT 으로 떨어졌고, 모델은 이모지만 붙이고 끝냈다(실제 화면).
        // ★ 맨 뒤에 둔다. 모델은 마지막 예시를 제일 무겁게 읽는다.
        s.add("\"제목이랑 참여 방법을 더 화려하게 해줘\"");
        s.add("{\"ops\":[{\"op\":\"STYLE\",\"target\":\"hero\",\"content\":null},"
                + "{\"op\":\"STYLE\",\"target\":\"steps\",\"content\":null}]}");
        s.add("");
        s.add("JSON 외에는 아무것도 출력하지 마라.");
        return s.toString();
    }

    // ── 동작 배치기 ─────────────────────────────────────────────────

    /**
     * 동작 배치기 — 방금 만든 백지 페이지에 "어떤 동작을 어디에" 둘지 JSON 으로만 고른다 (GenerationService).
     *
     * ★ 모델은 HTML · data-behavior 를 쓰지 않는다. 이름을 고르기만 하고, 검증 · 심기는 서버다(BehaviorPlanter.decide · apply)
     * ★ 이 목록은 Behavior.PLANNABLE 과 짝이다
     */
    public static String behaviorPlanner() {
        StringJoiner s = new StringJoiner("\n");
        s.add("너는 이벤트 페이지에 동작을 놓는 동작 배치기다. JSON 하나만 출력한다.");
        s.add("");
        s.add("동작:");
        s.add("- \"countdown\"    마감까지 남은 시간 표시");
        s.add("- \"scroll-to\"    다른 영역으로 내려가는 버튼. block 은 버튼을 둘 영역, target 은 이동할 영역");
        s.add("- \"participate\"  참여 버튼. 그 영역의 버튼에 붙는다 (보통 cta)");
        s.add("");
        s.add("출력 형식:");
        s.add("{\"actions\":[{\"behavior\":\"<동작>\",\"block\":\"<영역>\",\"target\":<영역 또는 null>}]}");
        s.add("");
        s.add("규칙:");
        s.add("- block · target 은 [페이지 영역] 에 있는 이름만 쓴다.");
        s.add("- 이벤트 요청문의 분위기에 맞는 동작만 고른다. 필요 없는 동작은 넣지 마라.");
        s.add("- 마감 · 기간 한정 · 선착순 같은 말이 있으면 hero 에 countdown 을 둔다.");
        s.add("- 혜택이나 참여 방법이 길면 hero 에 그 영역으로 가는 scroll-to 를 둔다.");
        s.add("- 참여 버튼(participate)은 cta 에 둔다.");
        s.add("- 위 목록에 없는 동작(투표 · 복주머니 · 룰렛 같은 게임 등)은 넣지 마라.");
        s.add("");
        s.add("예시:");
        s.add("\"이번 주말까지만 데이터 2배! 선착순 1000명\"");
        s.add("{\"actions\":[{\"behavior\":\"countdown\",\"block\":\"hero\",\"target\":null},"
                + "{\"behavior\":\"participate\",\"block\":\"cta\",\"target\":null}]}");
        s.add("");
        s.add("\"신규 가입 혜택 3가지와 가입 방법 안내\"");
        s.add("{\"actions\":[{\"behavior\":\"scroll-to\",\"block\":\"hero\",\"target\":\"benefits\"},"
                + "{\"behavior\":\"participate\",\"block\":\"cta\",\"target\":null}]}");
        s.add("");
        s.add("JSON 외에는 아무것도 출력하지 마라.");
        return s.toString();
    }

    /** 동작 배치기의 사용자 메시지 — 페이지 영역과 이벤트 요청문 */
    public static String behaviorPlannerUser(String requestText, List<Block> present) {
        StringJoiner names = new StringJoiner(", ");
        for (Block b : present) if (b != Block.NOTICES) names.add(b.key());
        StringJoiner s = new StringJoiner("\n");
        s.add("[페이지 영역] " + names);
        s.add("");
        s.add("[이벤트 요청문]");
        s.add(requestText == null ? "" : requestText.strip());
        return s.toString();
    }
}
