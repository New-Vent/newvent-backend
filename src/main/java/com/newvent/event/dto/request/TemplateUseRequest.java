package com.newvent.event.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.newvent.user.domain.MembershipGrade;

public record TemplateUseRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt,
        MembershipGrade grade) {}
