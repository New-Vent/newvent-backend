package com.newvent.generation.service;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 이벤트가 무엇인지 알 수 없을 만큼 내용이 없는 생성 요청인가.
 *
 * ★ 왜 — "이벤트", "ㅋㅋㅋ 아무거나 재밌게" 는 되묻기 없이 페이지가 만들어졌고, 모델이 빈칸을 숫자와 브랜드로
 *   채웠다("100% 당첨 기회", "갤럭시 S23", "총 100분", "5천원 상당"). 내용이 없으면 지어낼 수밖에 없다(#230).
 *
 * ★ **제목과 요청문이 둘 다** 비어 있을 때만 되묻는다
 *   이벤트에는 보통 이름이 있다. 제목이 "신규 가입 쿠폰" 이면 요청이 "만들어줘" 뿐이어도 만들 수 있다.
 *   요청문만 보고 되물으면 멀쩡한 이벤트를 막는다.
 *
 * ★ 판정은 일부러 거칠다 — 군말(만들어줘 · 이벤트 · 아무거나 …)과 기호를 빼고 남는 글자가 둘 미만이면 비었다고 본다.
 *   애매하면 만든다. 되묻는 쪽이 훨씬 번거롭고, 지어내기는 수치 검증({@link com.newvent.registry.ValueCheck})이 따로 막는다.
 */
public final class RequestVagueness {

    private RequestVagueness() { }

    /** 관리자에게 보이는 되묻기 문장 */
    public static final String QUESTION =
            "어떤 이벤트인지 알려 주세요. 혜택이나 대상을 한 줄만 적어 주셔도 됩니다. "
            + "(예: 신규 가입자에게 데이터 쿠폰 3GB를 드리는 이벤트)";

    /** 내용이 없는 말. 긴 말이 먼저 지워지도록 길이 순으로 쓴다 */
    private static final List<String> FILLER = List.of(
            "만들어주세요", "만들어줄래", "만들어줘요", "만들어줘", "만들어", "만들자", "만드세요",
            "작성해주세요", "작성해줘", "작성", "생성해주세요", "생성해줘", "생성",
            "해주세요", "해줘요", "해줘", "해봐", "부탁드려요", "부탁해요", "부탁해", "부탁",
            "아무거나", "알아서", "그냥", "재밌게", "재미있게", "멋지게", "예쁘게", "좋게", "잘",
            "이벤트", "페이지", "행사", "프로모션", "하나", "한번", "좀", "새로", "우리");
    private static final List<String> BY_LENGTH = FILLER.stream()
            .sorted(Comparator.comparingInt(String::length).reversed()).toList();

    /** 자모만 있는 글자(ㅋㅋㅋ · ㅎㅎ · ㅠㅠ)와 글자·숫자가 아닌 것 */
    private static final Pattern NOT_CONTENT = Pattern.compile("[ㄱ-ㅎㅏ-ㅣ]|[^\\p{L}\\p{N}]");

    /** 제목과 요청문이 둘 다 내용이 없는가 */
    public static boolean isTooVague(String title, String requestText) {
        return contentLength(title) < 2 && contentLength(requestText) < 2;
    }

    /** 군말을 뺀 뒤 남는 글자 수 */
    static int contentLength(String text) {
        if (text == null) return 0;
        String s = NOT_CONTENT.matcher(text).replaceAll("");
        for (String filler : BY_LENGTH) s = s.replace(filler, "");
        return s.length();
    }
}
