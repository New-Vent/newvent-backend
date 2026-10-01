package com.newvent.auth.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * JWT 비밀키 검사와 프로파일별 인증 설정값을 본다. 스프링 컨텍스트 없이 돈다.
 *
 * ★ 설정 파일은 **시스템 환경변수를 뺀 채** 바인딩한다.
 *   "아무 환경변수 없이 뜨면 이 값이 나온다" 를 보려는 것이다. 환경변수를 두면 개발자 PC · CI 값이 섞인다
 *   (CI 는 CORS_ALLOWED_ORIGINS · AUTH_COOKIE_SECURE 를 secrets 로 넣는다).
 */
class AuthPropsTest {

    private static AuthProps.Jwt jwt(String secret) {
        return new AuthProps.Jwt(secret, 30, 7);
    }

    /** 시스템 환경변수 · 시스템 속성 없이, 설정 파일만 올린 환경 — 뒤에 준 파일이 우선 */
    private static StandardEnvironment envWithoutSystem(String... files) throws IOException {
        var env = new StandardEnvironment();
        var sources = env.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

        var loader = new YamlPropertySourceLoader();
        for (String file : files) {
            Resource yaml = new ClassPathResource(file);
            loader.load(file, yaml).forEach(sources::addFirst);
        }
        return env;
    }

    /** application.yaml 위에 프로파일 파일을 얹어 auth.* 를 바인딩한다 */
    private static AuthProps bindWithoutEnv(String... files) throws IOException {
        return Binder.get(envWithoutSystem(files)).bind("auth", AuthProps.class).get();
    }

    private static Throwable rootCause(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        return e;
    }

    @Test
    @DisplayName("JWT_SECRET 이 없으면(빈 값 · 치환 안 된 ${JWT_SECRET}) 이유를 적고 멈춘다")
    void 비밀키_없음() {
        for (String missing : new String[] {null, "", "   ", "${JWT_SECRET}"}) {
            var e = assertThrows(IllegalStateException.class, () -> jwt(missing));
            assertTrue(e.getMessage().contains("JWT_SECRET 이 없습니다"), missing);
        }
    }

    @Test
    @DisplayName("32바이트 미만이면 멈추고, 메시지에는 길이만 싣고 값은 싣지 않는다")
    void 비밀키_짧음() {
        String shortSecret = "short-secret-16b";
        var e = assertThrows(IllegalStateException.class, () -> jwt(shortSecret));
        assertTrue(e.getMessage().contains("16바이트"));
        assertFalse(e.getMessage().contains(shortSecret));
    }

    @Test
    @DisplayName("32바이트부터 통과한다")
    void 비밀키_경계() {
        assertThrows(IllegalStateException.class, () -> jwt("a".repeat(31)));
        assertDoesNotThrow(() -> jwt("a".repeat(32)));
    }

    /**
     * ★ dev 기본 비밀키는 두 조건을 같이 지켜야 한다.
     *   32바이트 이상   — 아니면 환경변수 없이 dev 로 뜨지 않는다
     *   "dev-only" 포함 — 개발용 값임을 한눈에 알아볼 수 있게
     */
    @Test
    @DisplayName("dev: 환경변수 없이 Secure 없음 · Lax · Vite 5173·5174 허용 · 개발용 표식이 있는 비밀키")
    void dev_기본값() throws IOException {
        AuthProps dev = bindWithoutEnv("application.yaml", "application-dev.yaml");

        assertFalse(dev.cookie().secure());
        assertEquals("Lax", dev.cookie().sameSite());
        assertEquals(List.of("http://localhost:5173", "http://localhost:5174"), dev.cors().allowedOrigins());
        assertTrue(dev.jwt().secret().contains("dev-only"));
        assertEquals(30, dev.jwt().accessTtlMinutes());
    }

    @Test
    @DisplayName("prod: 환경변수가 없으면 비밀키가 없어 바인딩 단계에서 멈춘다 — 기본값으로 뜨지 않는다")
    void prod_환경변수_없음() {
        var e = assertThrows(BindException.class, () -> bindWithoutEnv("application.yaml", "application-prod.yaml"));
        assertTrue(rootCause(e).getMessage().contains("JWT_SECRET 이 없습니다"));
    }

    @Test
    @DisplayName("prod: 설정 파일 자체는 Secure · Lax 이고 허용 Origin 기본값이 없다")
    void prod_설정값() throws IOException {
        var env = envWithoutSystem("application.yaml", "application-prod.yaml");

        assertEquals("true", env.getProperty("auth.cookie.secure"));
        assertEquals("Lax", env.getProperty("auth.cookie.same-site"));
        // 기본값이 없다 — 배포 환경변수로 반드시 넣어야 한다 (없으면 치환되지 않아 예외)
        assertThrows(IllegalArgumentException.class, () -> env.getProperty("auth.cors.allowed-origins"));
    }
}
