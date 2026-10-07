package com.newvent.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TemplateRegisterRequest(
        @NotNull @Positive Long eventId,
        @NotNull @Positive Long sourceVersionId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description) {}
