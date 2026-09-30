package com.newvent.auth.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auth.* 설정을 담는다. **환경마다 달라지는 값만 여기 둔다.**
 *
 *   dev   application-dev.yaml    Secure=false, 허용 Origin=localhost:5173·5174, 개발용 비밀키
 *   prod  application-prod.yaml   Secure=true,  허용 Origin=배포 도메인, JWT_SECRET 필수
 *
 * ★ 쿠키 이름과 Path 는 여기 없다 — 환경과 무관해야 해서 AccountType 에 고정했다.
 *
 * ★ SameSite=None 은 Secure 가 필수다 (브라우저가 거부한다). 여기서 조합을 검사한다.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProps(Jwt jwt, Cookie cookie, Cors cors) {

    public AuthProps {
        if (cors == null) cors = new Cors(List.of());
        if ("None".equalsIgnoreCase(cookie.sameSite()) && !cookie.secure()) {
            throw new IllegalStateException("auth.cookie.same-site=None 은 auth.cookie.secure=true 와 같이 써야 합니다.");
        }
    }

    /**
     * ★ 비밀키는 설정을 읽는 순간 검사한다 — 프로파일과 무관하게, 다른 빈보다 먼저.
     *
     *   없음    JWT_SECRET 이 없으면 값이 비는 게 아니라 "${JWT_SECRET}" 글자 그대로 들어온다.
     *           그대로 두면 JWT 라이브러리가 "104 bits ... not secure enough" 로 죽어 원인을 알기 어렵고,
     *           자리표시가 32바이트를 넘었다면 누구나 아는 값으로 서명한 채 떴을 것이다.
     *   짧음    HS256 키는 32바이트 이상이어야 한다.
     *
     * 값은 메시지에 싣지 않는다 (로그로 새지 않게) — 길이만 알려준다.
     */
    public record Jwt(String secret, long accessTtlMinutes, long refreshTtlDays) {

        static final int MIN_SECRET_BYTES = 32;

        public Jwt {
            if (secret == null || secret.isBlank() || secret.contains("${")) {
                throw new IllegalStateException(
                        "JWT_SECRET 이 없습니다. 환경변수로 넣으세요 (openssl rand -base64 48).");
            }
            int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
            if (bytes < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET 이 너무 짧습니다 (" + bytes + "바이트). "
                        + MIN_SECRET_BYTES + "바이트 이상이어야 합니다 (openssl rand -base64 48).");
            }
        }
    }

    /** Refresh Token 쿠키의 보안 속성. 이름·Path 는 AccountType 이 정한다 */
    public record Cookie(boolean secure, String sameSite) { }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            if (allowedOrigins == null) allowedOrigins = List.of();
        }
    }
}
