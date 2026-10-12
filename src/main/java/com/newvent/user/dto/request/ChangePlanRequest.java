package com.newvent.user.dto.request;

import jakarta.validation.constraints.Positive;

import io.swagger.v3.oas.annotations.media.Schema;

public record ChangePlanRequest(
        @Schema(description = "새 월 요금(원). 1 이상", example = "35000")
        @Positive int plan) {}
