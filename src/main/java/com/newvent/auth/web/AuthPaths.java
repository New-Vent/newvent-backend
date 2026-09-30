package com.newvent.auth.web;

/**
 * 인증 경로 상수. **@PostMapping 에 쓰려고 컴파일 상수로 둔다** (enum 값은 애너테이션에 못 쓴다).
 *
 * 경로를 조합하거나 목록이 필요하면 AccountType 을 쓴다. 여기 값을 다른 파일에 다시 적지 말 것.
 */
public final class AuthPaths {

    private AuthPaths() {}

    public static final String USER = "/api/auth";
    public static final String ADMIN = "/api/admin/auth";

    public static final String LOGIN = "/login";
    public static final String REFRESH = "/refresh";
    public static final String LOGOUT = "/logout";
}
