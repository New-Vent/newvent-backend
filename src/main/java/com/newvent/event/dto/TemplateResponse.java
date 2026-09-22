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
                template.templateKey(),
                template.name(),
                template.description(),
                template.theme(),
                template.active());
    }
}
