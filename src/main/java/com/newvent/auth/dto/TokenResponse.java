package com.newvent.auth.dto;

import java.time.Instant;

public record TokenResponse(String accessToken, Instant expireDate) {
}
