package com.newvent.participation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SportsResultRequest(
    @NotBlank String winningPrediction
) {
}
