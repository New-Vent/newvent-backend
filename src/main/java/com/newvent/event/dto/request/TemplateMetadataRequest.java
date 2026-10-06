package com.newvent.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이름/설명만 변경한다. 원본 HTML은 변경하지 않는다. */
public record TemplateMetadataRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description) {}
