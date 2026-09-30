package com.newvent.common.response;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 실패 응답.
 *
 *  success 를 싣는 이유 — {@link ApiResponse} 와 갈라 읽을 수 있게 한다.
 *   이게 없으면 클라이언트가 성공/실패를 HTTP 상태로 먼저 갈라야 하고,
 *   그러면 성공 응답의 success 필드는 아무 일도 하지 않는 장식이 된다.
 *   실패 응답에서 success 를 읽으면 undefined 라 {@code body.success === false} 로도
 *   판별할 수 없었다 — 그래서 늘 false 를 명시해서 내려보낸다.
 *
 *   클라이언트는 이 한 줄로 갈린다.
 *   {@code if (body.success) return body.data; else throw new ApiError(body.message) }
 */
public record ErrorResponse(boolean success, String code, String message, LocalDateTime timestamp) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message, LocalDateTime.now(SEOUL_ZONE_ID));
    }
}
