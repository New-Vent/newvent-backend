package com.newvent.user.web;

import jakarta.validation.constraints.Positive;

public record ChangePlanRequest(@Positive int plan) {}
