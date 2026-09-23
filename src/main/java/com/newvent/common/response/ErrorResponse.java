package com.newvent.common.response;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record ErrorResponse(String code, String message, LocalDateTime timestamp) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, LocalDateTime.now(SEOUL_ZONE_ID));
    }
}
