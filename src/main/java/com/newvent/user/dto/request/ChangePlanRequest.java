package com.newvent.user.dto.request;

import jakarta.validation.constraints.Positive;

public record ChangePlanRequest(@Positive int plan) {}
