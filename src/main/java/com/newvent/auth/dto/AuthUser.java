package com.newvent.auth.dto;

/**
 * 로그인 주체. SecurityContext 의 principal 이자 Refresh Token 의 주인.
 *
 * users / admins 는 별도 테이블이라 id 만으로는 누구인지 알 수 없다 — admin 으로 어느 테이블의 id 인지 구분한다.
 * Access Token 의 claim 에서 복원한다 (요청마다 DB 를 안 본다).
 */
public record AuthUser(Long id, boolean admin) {

    public static AuthUser user(Long id) {
        return new AuthUser(id, false);
    }

    public static AuthUser admin(Long id) {
        return new AuthUser(id, true);
    }
}
