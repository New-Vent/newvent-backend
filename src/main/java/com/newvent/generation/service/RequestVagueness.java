package com.newvent.generation.service;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

// **제목과 요청문이 둘 다** 비어 있을 때만 되묻기
// 만들어줘 · 이벤트 · 아무거나 등등 기호를 빼고 남는 글자가 둘 미만이면 비었다고 본다.
public final class RequestVagueness {

    private RequestVagueness() { }

    // 관리자에게 보이는 되묻기 문장
    public static final String QUESTION =
            "어떤 이벤트인지 알려 주세요. 혜택이나 대상을 한 줄만 적어 주셔도 됩니다. "
            + "(예: 신규 가입자에게 데이터 쿠폰 3GB를 드리는 이벤트)";

    // 내용이 없는 말. 긴 말이 먼저 지워지도록 길이 순으로
    private static final List<String> FILLER = List.of(
            "만들어주세요", "만들어줄래", "만들어줘요", "만들어줘", "만들어", "만들자", "만드세요",
            "작성해주세요", "작성해줘", "작성", "생성해주세요", "생성해줘", "생성",
            "해주세요", "해줘요", "해줘", "해봐", "부탁드려요", "부탁해요", "부탁해", "부탁",
            "아무거나", "알아서", "그냥", "재밌게", "재미있게", "멋지게", "예쁘게", "좋게", "잘",
            "이벤트", "페이지", "행사", "프로모션", "하나", "한번", "좀", "새로", "우리");
    private static final List<String> BY_LENGTH = FILLER.stream()
            .sorted(Comparator.comparingInt(String::length).reversed()).toList();

    // 자음이나 모음만 있는 글자(ㅋㅋㅋ · ㅎㅎ · ㅠㅠ)와 글자·숫자가 아닌 것
    private static final Pattern NOT_CONTENT = Pattern.compile("[ㄱ-ㅎㅏ-ㅣ]|[^\\p{L}\\p{N}]");

    // 제목과 요청문이 둘 다 내용이 없는가
    public static boolean isTooVague(String title, String requestText) {
        return contentLength(title) < 2 && contentLength(requestText) < 2;
    }

    private static final Pattern KNOWN = Pattern.compile("\"known\"\\s*:\\s*(true|false)");

    // 요청 판정기(모델)의 답에서 "이벤트를 알 수 있는가" 를 읽는다 
    public static Boolean parseKnown(String reply) {
        if (reply == null) return null;
        var m = KNOWN.matcher(reply);
        return m.find() ? Boolean.valueOf(m.group(1)) : null;
    }

    // 글자도 숫자도 없는 입력인가 — "...", "!!!???", 이모지뿐. 모델 판정 없이 되묻기
    public static boolean hasNoLetters(String text) {
        return text == null || NOT_CONTENT.matcher(text).replaceAll("").isEmpty();
    }

    // 군말을 뺀 뒤 남는 글자 수
    static int contentLength(String text) {
        if (text == null) return 0;
        String s = NOT_CONTENT.matcher(text).replaceAll("");
        for (String filler : BY_LENGTH) s = s.replace(filler, "");
        return s.length();
    }
}
