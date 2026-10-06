package com.newvent.event.dto.response;

import com.newvent.event.domain.EventTemplate;

public record TemplateLibraryResponse(
        String templateKey, String name, String description,
        boolean builtin, boolean active, String thumbnailPath) {
    public static TemplateLibraryResponse from(EventTemplate t) {
        return new TemplateLibraryResponse(t.getCode(), t.getName(), t.getDescription(),
                t.isBuiltin(), t.isActive(), t.getThumbnailPath());
    }
}
