package com.newvent.event.dto;

import com.newvent.event.domain.EventTemplate;

public record TemplateResponse(
        String templateKey,
        String name,
        String description,
        String theme,
        boolean active
) {
    public static TemplateResponse from(EventTemplate template) {
        return new TemplateResponse(
                template.getCode(),
                template.getName(),
                template.getDescription(),
                themeOf(template.getCode()),
                template.isActive());
    }

    /** Entity 에 theme 컬럼이 없어 code 기준으로 화면용 값을 둔다. */
    private static String themeOf(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "sports_cheer" -> "theme-sports";
            case "holiday_gift" -> "theme-holiday";
            case "member_appreciation" -> "theme-vip";
            case "flash_sale" -> "theme-sale";
            case "pre_registration" -> "theme-launch";
            default -> null;
        };
    }
}
