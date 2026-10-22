package com.newvent.common.util;

import java.util.Locale;

// LIKE 검색 패턴 만들기
public final class LikePattern {

    public static final char ESCAPE = '!';

    private LikePattern() {}

    // 검색어를 소문자 {@code "%값%"} 패턴으로, 앞뒤 공백은 떼고, 비었으면 조건 없음(null).
    public static String contains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escape = String.valueOf(ESCAPE);
        String escaped = keyword.strip().toLowerCase(Locale.ROOT)
                .replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
        return "%" + escaped + "%";
    }
}
