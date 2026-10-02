package com.newvent.auth.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.annotation.Profile;

/** prod 인증 설정을 빈 생성 시 검사한다. 설정값은 오류 메시지에 포함하지 않는다. */
@Profile("prod")
public final class ProdAuthGuard {

    private static final Set<String> DEVELOPMENT_SECRETS = Set.of(
            "newvent-dev-only-jwt-secret-do-not-use-in-prod",
            "test-only-not-a-real-secret-0123456789abcdef");

    public ProdAuthGuard(AuthProps props) {
        if (!props.cookie().secure()) {
            throw new IllegalStateException("prod 에서는 auth.cookie.secure=true 여야 합니다.");
        }
        if (DEVELOPMENT_SECRETS.contains(props.jwt().secret())) {
            throw new IllegalStateException("prod 에서는 개발·테스트용 JWT_SECRET 을 사용할 수 없습니다.");
        }
        if (props.cors().allowedOrigins().isEmpty()) {
            throw invalidOrigins();
        }
        for (String origin : props.cors().allowedOrigins()) {
            validateOrigin(origin);
        }
    }

    private static void validateOrigin(String origin) {
        if (origin == null || origin.isBlank() || origin.contains("${") || origin.contains("*")) {
            throw invalidOrigins();
        }
        try {
            URI uri = new URI(origin);
            String host = uri.getHost();
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || host == null || uri.getRawUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() < -1 || uri.getPort() > 65535 || uri.getPort() == 0) {
                throw invalidOrigins();
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            if (host.equals("localhost") || host.endsWith(".localhost") || host.startsWith("127.")
                    || host.equals("[::1]") || host.equals("[0:0:0:0:0:0:0:1]")) {
                throw new IllegalStateException("prod 의 CORS_ALLOWED_ORIGINS 에 localhost·루프백 주소를 허용할 수 없습니다.");
            }
        } catch (URISyntaxException e) {
            throw invalidOrigins();
        }
    }

    private static IllegalStateException invalidOrigins() {
        return new IllegalStateException(
                "prod 의 CORS_ALLOWED_ORIGINS 가 없거나 올바르지 않습니다. 경로·끝 슬래시 없는 http(s) Origin 을 명시하세요.");
    }
}
