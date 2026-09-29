package com.newvent.event.dto.request;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * CTA 참여 버튼 스타일 변경 레코드.
 *
 * @param background 버튼 배경색 (^#[0-9a-fA-F]{6}$)
 * @param color      버튼 글자색 (^#[0-9a-fA-F]{6}$)
 * @param size       버튼 크기 (small | medium | large)
 * @param shape      버튼 모서리 모양 (square | round | pill)
 */
public record ButtonStyle(String background, String color, String size, String shape) {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> ALLOWED_SIZES = Set.of("small", "medium", "large");
    private static final Set<String> ALLOWED_SHAPES = Set.of("square", "round", "pill");

    public ButtonStyle {
        background = normalizeHexColor("background", background);
        color = normalizeHexColor("color", color);
        size = normalizeChoice("size", size, ALLOWED_SIZES);
        shape = normalizeChoice("shape", shape, ALLOWED_SHAPES);
    }

    private static String normalizeHexColor(String fieldName, String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!HEX_COLOR_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(fieldName + " 은(는) ^#[0-9a-f]{6} 형식이어야 합니다. 입력: " + value);
        }
        return trimmed.toLowerCase();
    }

    private static String normalizeChoice(String fieldName, String value, Set<String> allowed) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!allowed.contains(trimmed)) {
            throw new IllegalArgumentException(fieldName + " 값은 " + allowed + " 중 하나여야 합니다. 입력: " + value);
        }
        return trimmed;
    }
}
