package com.newvent.auth.dto;

import java.time.Instant;

/**
 * 로그인 · 재발급 응답 본문 (ApiResponse.data 로 감싸서 나간다).
 *
 * @param accessToken Authorization: Bearer 로 보낼 JWT
 * @param expireDate  만료 시각
 * @param expiresIn   만료까지 남은 초. 프론트가 만료 직전 미리 갱신하는 데 쓴다 —
 *                    클라이언트 시계가 틀려도 영향이 없도록 시각이 아니라 남은 시간으로 준다
 */
public record TokenResponse(String accessToken, Instant expireDate, long expiresIn) {
}
