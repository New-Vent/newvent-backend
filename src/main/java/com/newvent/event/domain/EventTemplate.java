package com.newvent.event.domain;

/**
 * 기본 템플릿 5종. HTML 본문(baseContent)은 아직 붙이지 않는다.
 * 출처: 이헌진 추천 템플릿 (template_1~5).
 */
public record EventTemplate(
        String templateKey,
        String name,
        String description,
        String theme,
        boolean active
) {
}
