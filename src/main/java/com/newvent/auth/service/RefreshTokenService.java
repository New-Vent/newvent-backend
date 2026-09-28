package com.newvent.auth.service;

import java.time.Duration;

import com.newvent.auth.dto.AuthUser;

/**
 * Refresh Token 발급·회전·폐기.
 *
 * 토큰은 JWT 가 아니라 무작위 문자열이고, DB 에는 SHA-256 해시만 둔다.
 * DB 가 털려도 쿠키 원문은 만들 수 없다.
 *
 */
public interface RefreshTokenService {

    Duration ttl();

    String issue(AuthUser owner);

    /**토큰을 1회 사용 처리하고 돌려준다. 새 토큰은 발급하지 않는다. */
    AuthUser consume(String raw);

    /** 로그아웃. 없는 토큰이어도 조용히 넘어간다. */
    void revoke(String raw);

    /**
     * @return 폐기한 토큰 수
     */
    int revokeAll(AuthUser owner);

    /**
     * @return 삭제한 행 수
     */
    int purgeExpired();
}
