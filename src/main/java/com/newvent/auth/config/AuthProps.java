package com.newvent.auth.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auth.* 설정을 담는다.
 *
 * ★ SameSite=None 은 Secure 가 필수다 (브라우저가 거부한다).
 *   dev 프로파일은 Secure=false / SameSite=Lax 로 내려가야 하므로 여기서 조합을 검사한다.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProps(Jwt jwt, Cookie cookie, Cors cors) {

    public AuthProps {
        if (cors == null) cors = new Cors(List.of());
        if ("None".equalsIgnoreCase(cookie.sameSite()) && !cookie.secure()) {
            throw new IllegalStateException("auth.cookie.same-site=None 은 auth.cookie.secure=true 와 같이 써야 합니다.");
        }
    }

    public record Jwt(String secret, long accessTtlMinutes, long refreshTtlDays) { }

    /** Refresh Token 쿠키 */
    public record Cookie(String name, String path, boolean secure, String sameSite) { }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            if (allowedOrigins == null) allowedOrigins = List.of();
        }
    }
}
