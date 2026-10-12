package com.newvent.event.dto.request;

import java.util.Set;
import java.util.regex.Pattern;

import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * CTA 참여 버튼 스타일 변경 레코드.
 *
 * @param background 버튼 배경색 (^#[0-9a-fA-F]{6}$)
 * @param color      버튼 글자색 (^#[0-9a-fA-F]{6}$)
 * @param size       버튼 크기 (small | medium | large)
 * @param shape      버튼 모서리 모양 (square | round | pill)
 */
@Schema(description = "참여 버튼 스타일. 비운 항목은 바꾸지 않고, 허용하지 않는 값은 400")
public record ButtonStyle(
        @Schema(description = "배경색 #RRGGBB", example = "#1a73e8") String background,
        @Schema(description = "글자색 #RRGGBB", example = "#ffffff") String color,
        @Schema(allowableValues = {"small", "medium", "large"}) String size,
        @Schema(allowableValues = {"square", "round", "pill"}) String shape) {

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
            throw new DirectEditException(DirectEditErrorCode.INVALID_BUTTON_STYLE);
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
            throw new DirectEditException(DirectEditErrorCode.INVALID_BUTTON_STYLE);
        }
        return trimmed;
    }
}
