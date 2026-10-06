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
        s.add("더할 수 있는 영역: (요청문이 그 내용을 직접 말할 때만 만든다. 추측해서 만들지 마라)");
        s.add("- 요청문에 질문·답이 없으면 faq 를 만들지 마라. '별도 공지', '고객센터 문의' 같은 내용 없는 답은 쓰지 마라.");
        s.add("- 요청문에 비교할 값이 없으면 compare 를 만들지 마라.");
        s.add("- 요청문이 주의·유의 사항을 강조해 달라고 하면 highlight 를 만들고 v-highlight-alert 를 고른다.");
        s.add("- 같은 영역(data-block)을 두 번 만들지 마라. highlight 도 하나뿐이다.");
        // ★ 목록에서 빼기만 하면 모델이 이름을 몰라도 비슷한 걸 만든다. 빠진 이름을 직접 말한다.
        //   그래도 만들면 서버가 지운다(BlockValidator.dropUntriggered).
        List<String> skipped = Block.llmBlocks().stream()
                .filter(b -> !blocks.contains(b)).map(Block::key).toList();
        if (!skipped.isEmpty()) {
            s.add("- 이번 요청에서는 " + String.join(", ", skipped) + " 영역을 만들지 마라.");
        }
        // ★ 반대쪽도 이름을 댄다 — 요청문이 직접 요청한 블록은 "만들 수 있다" 가 아니라 "만든다".
        //   이름 없이 "직접 말할 때만" 이라고만 하면 모델이 혜택 블록에 섞어 넣고 끝낸다(Bedrock 실측).
        List<String> required = blocks.stream()
                .filter(b -> !b.core() && b.requestedBy(requestText)).map(Block::key).toList();
        if (!required.isEmpty()) {
            s.add("- 이번 요청에서는 " + String.join(", ", required)
                    + " 영역을 반드시 만든다. 요청문이 직접 요청했다. 다른 영역에 섞어 넣지 마라.");
        }
        for (Block b : blocks) {
            if (b.core()) continue;
            addBlockLine(s, b);
        }
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
        // ★ 예시에 benefits 와 steps 를 넣는다.
        //   예전 예시는 hero 와 cta 둘뿐이었고, 출력도 딱 그 정도로 나왔다.
        //   라우터에서 이미 겪은 것과 같다 — 예시가 있고 없고가 실제로 갈렸다.
        //   분량을 말로만 시키는 것보다 보여주는 쪽이 세다.
        s.add("예:");
        s.add("<section data-block=\"hero\" class=\"v-hero-center palette-summer\">");
        s.add("  <h1>여름 데이터 대방출</h1>");
        s.add("  <p>이번 여름, 데이터 걱정 없이 마음껏 즐기세요.</p>");
        s.add("</section>");
        s.add("<section data-block=\"benefits\" class=\"v-benefits-grid v-surface-tint\">");
        s.add("  <ul>");
        s.add("    <li>데이터 3GB 즉시 지급 — 가입 완료 즉시 사용할 수 있습니다.</li>");
        s.add("    <li>월 요금 30% 할인 — 가입 후 6개월 동안 적용됩니다.</li>");
        s.add("  </ul>");
        s.add("</section>");
        s.add("<section data-block=\"steps\" class=\"v-steps-timeline\">");
        s.add("  <ol>");
        s.add("    <li>이벤트 페이지에서 로그인합니다.</li>");
        s.add("    <li>원하는 요금제를 선택합니다.</li>");
        s.add("    <li>신청하기를 눌러 응모를 완료합니다.</li>");
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
        s.add("- 혜택은 요청문에 있는 것을 빠짐없이 담고, 항목마다 한 문장으로 설명을 붙인다.");
        //   반대로 참여 방법은 모델이 만드는 영역이다(레지스트리 Source.LLM).
        //   절차를 지어내는 것은 허용이고, 그래서 요청문에 없어도 만들라고 시킨다.
        s.add("- 참여 방법은 3단계로 쓴다. 요청문에 절차가 없으면 일반적인 온라인 응모 절차로 쓴다.");
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
        if (b.source() == Block.Source.SERVER) {
            throw new IllegalArgumentException(
                    b.key() + " 는 서버 소유입니다. 모델에게 수정시키면 안 됩니다.");
        }
        // ★ 모양·색 요청의 길. 고를 게 없으면 관련 문장을 통째로 뺀다 —
        //   "모양 고르기" 를 언급만 하고 목록이 없으면 모델이 목록 밖 이름을 지어낸다.
        List<Variant> variants = templateBlock ? List.of() : Variant.of(b);
        boolean palette = b == Block.HERO && allowPalette;
        boolean looks = !variants.isEmpty() || palette;

        StringJoiner s = new StringJoiner("\n");
        s.add("너는 이벤트 페이지의 영역 하나를 수정하는 도우미다.");
        s.add("");
        s.add("<section data-block=\"" + b.key() + "\"> 영역만 수정해서 그 영역만 출력한다.");
        s.add("이 영역의 역할: " + b.desc());
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
            s.add("모양 고르기: (모양·색·분위기를 바꿔 달라는 요청일 때만. 문구는 그대로 둔다)");
            s.add("- <section> 의 class 에 아래 이름을 붙이거나 다른 이름으로 바꾼다. 목록에 없는 이름은 쓰지 마라.");
            addVariantLines(s, variants);
            // ★ 색을 콕 집어 말하면 그 색 이름의 배경을 고르라고 못 박는다.
            //   안 그러면 "파란색으로" 에 어두운 배경 · 그림자 같은 엉뚱한 걸 고르고 성공으로 끝난다
            if (variants.stream().anyMatch(Variant::namedColor)) {
                s.add("- 특정 색(파란색 · 초록색 …)으로 바꿔 달라면 배경에서 그 색 이름이 적힌 것을 고른다.");
            }
            if (palette) {
                s.add("- 페이지 전체 색감: 아래 중 하나 (하나만). 페이지 전체에 적용된다.");
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
        s.add("- \"STYLE\"   색·크기·굵기 등 겉모양만 바꾼다");
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
        s.add("JSON 외에는 아무것도 출력하지 마라.");
        return s.toString();
    }
}
