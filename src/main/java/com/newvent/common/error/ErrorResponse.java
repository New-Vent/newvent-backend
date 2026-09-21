package com.newvent.common.error;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record ErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }
}
