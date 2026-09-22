package com.newvent.registry;

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

    /** 생성용 — 페이지 전체를 한 번에 만든다 */
    public static String generate() {
        StringJoiner s = new StringJoiner("\n");
        s.add("너는 통신사 이벤트 페이지를 만드는 도우미다.");
        s.add("");
        s.add("출력 규칙:");
        s.add("- 각 영역은 <section data-block=\"이름\"> ... </section> 으로 감싼다.");
        s.add("- <html>, <head>, <body> 태그를 쓰지 마라.");
        s.add("- 코드블록으로 감싸지 마라.");
        s.add("- 설명, 인사말, 마무리 멘트를 붙이지 마라.");
        s.add("");
        s.add("만들 영역:");
        for (Block b : Block.llmBlocks()) {
            String tail = b.required() ? "" : "  (선택)";
            s.add("- data-block=\"" + b.key() + "\" : " + b.desc() + tail);
            if (b.shape() != null) {
                s.add("    형태: " + b.shape());
            }
        }
        if (!Block.serverBlocks().isEmpty()) {
            s.add("");
            s.add("만들면 안 되는 영역:");
            for (Block b : Block.serverBlocks()) {
                s.add("- data-block=\"" + b.key() + "\" 는 절대 만들지 마라. " + b.desc());
            }
        }
        s.add("");
        s.add("태그를 반드시 쓴다. 맨 텍스트만 두지 마라.");
        s.add("예:");
        s.add("<section data-block=\"hero\">");
        s.add("  <h1>여름 데이터 대방출</h1>");
        s.add("  <p>이번 여름 데이터 걱정 없이</p>");
        s.add("</section>");
        s.add("<section data-block=\"cta\">");
        s.add("  <a href=\"#\" class=\"btn\">참여하기</a>");
        s.add("</section>");
        s.add("");
        s.add("금지:");
        s.add("- 날짜를 임의로 만들지 마라. 기간은 주어진 값만 쓴다.");
        s.add("- 주어지지 않은 혜택이나 수치를 만들어내지 마라.");
        s.add("- 대괄호 자리표시자를 절대 남기지 마라. 값을 모르면 그 문장을 아예 빼라.");
        s.add("- 실존하는 방송 프로그램, 브랜드, 연예인 이름을 쓰지 마라.");
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

    /** 수정용 — 블록 하나만 주고 하나만 받는다 */
    public static String edit(Block b) {
        if (b.source() == Block.Source.SERVER) {
            throw new IllegalArgumentException(
                    b.key() + " 는 서버 소유입니다. 모델에게 수정시키면 안 됩니다.");
        }
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
        s.add("- class 를 바꾸거나 지우지 마라. 디자인과 버튼 동작이 class 에 걸려 있다.");
        s.add("- id 를 지우거나 새로 만들지 마라. 화면 기능이 id 로 요소를 찾는다.");
        s.add("- <button> 을 <a> 나 <div> 로 바꾸지 마라.");
        // ★ 이모지 보존을 명시한다. 템플릿 블록 안에 58개가 있고,
        //   그중 일부는 슬롯 옆에 붙어 있다(📅 기간).
        //   지우는 것은 요청받지 않은 변경이고 검증기가 못 잡는다.
        s.add("- 원래 있던 이모지를 지우지 마라. 문구를 바꿀 때도 그대로 둔다.");
        s.add("");
        s.add("금지:");
        s.add("- 요청받은 것만 바꿔라. href, class 같은 기존 속성은 그대로 둔다.");
        s.add("- href=\"#\" 는 그대로 둬라. 실제 주소를 만들어 넣지 마라.");
        s.add("- 날짜를 임의로 만들지 마라.");
        s.add("- 대괄호 자리표시자를 남기지 마라.");
        return s.toString();
    }

    /** 라우터 — 영역 목록도 레지스트리에서 나온다 */
    public static String router() {
        StringJoiner names = new StringJoiner(", ");
        for (Block b : Block.values()) names.add(b.key());

        StringJoiner s = new StringJoiner("\n");
        s.add("너는 사용자 요청을 분류하는 라우터다. JSON 하나만 출력한다.");
        s.add("");
        s.add("영역 이름: " + names);
        s.add("");
        s.add("출력 형식:");
        s.add("{\"op\":\"<동작>\",\"target\":\"<영역 이름 또는 null>\"}");
        s.add("");
        s.add("동작:");
        s.add("- \"EDIT\"    특정 영역의 내용을 고친다");
        s.add("- \"ADD\"     없는 영역을 새로 넣는다");
        s.add("- \"DELETE\"  영역을 통째로 지운다");
        s.add("- \"STYLE\"   색·크기·굵기 등 겉모양만 바꾼다");
        s.add("");
        s.add("JSON 외에는 아무것도 출력하지 마라.");
        return s.toString();
    }
}
