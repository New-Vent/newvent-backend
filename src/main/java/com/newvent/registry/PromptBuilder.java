package com.newvent.registry;

import java.util.StringJoiner;

/**
 * 레지스트리에서 시스템 프롬프트를 만든다.
 *
 * 벤치마크 v8.4 의 build_system() 을 그대로 옮긴 것입니다.
 * 이 프롬프트로 S군 140/140, N군(형태 규칙 뺀 것) 0/70 이 나왔습니다.
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
        s.add("- 이모지를 쓰지 마라.");
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
        if (b.shape() != null) {
            s.add("형태: " + b.shape());
        }
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
        s.add("");
        s.add("금지:");
        s.add("- 요청받은 것만 바꿔라. href, class 같은 기존 속성은 그대로 둔다.");
        s.add("- href=\"#\" 는 그대로 둬라. 실제 주소를 만들어 넣지 마라.");
        s.add("- 날짜를 임의로 만들지 마라.");
        s.add("- 대괄호 자리표시자를 남기지 마라.");
        s.add("- 이모지를 쓰지 마라.");
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
