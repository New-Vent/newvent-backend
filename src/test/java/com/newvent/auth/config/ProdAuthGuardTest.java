package com.newvent.auth.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;

class ProdAuthGuardTest {
    private static final String KEY = "integration-production-key-32-bytes-minimum";
    private static final String ORIGIN = "https://newvent.duckdns.org";

    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                })
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Config.class)
                .withPropertyValues("spring.profiles.active=" + profile);
    }

    private ApplicationContextRunner prod() {
        return runner("prod").withPropertyValues("JWT_SECRET=" + KEY);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProps.class)
    @Import(ProdAuthGuard.class)
    static class Config { }

    private void assertStopped(ApplicationContextRunner runner, String message) {
        runner.run(context -> {
            assertNotNull(context.getStartupFailure());
            Throwable cause = context.getStartupFailure();
            while (cause.getCause() != null) cause = cause.getCause();
            assertTrue(cause.getMessage().contains(message), cause.getMessage());
            assertFalse(cause.getMessage().contains(KEY));
        });
    }

    @Test
    void missingCorsStopsContextEvenWithValidJwt() {
        assertStopped(prod(), "CORS_ALLOWED_ORIGINS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "${CORS_ALLOWED_ORIGINS}", "*",
            "https://*.example.com", "https://example.com/", "https://example.com/path",
            "https://example.com?x=1", "https://example.com#section", "https://user@example.com",
            "example.com", "ftp://example.com", "https://example.com:65536", "https://example.com:0"})
    void malformedCorsStopsContext(String origin) {
        assertStopped(prod().withPropertyValues("CORS_ALLOWED_ORIGINS=" + origin), "CORS_ALLOWED_ORIGINS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "http://localhost:5174",
            "https://LOCALHOST", "https://localhost.", "http://app.localhost",
            "http://127.0.0.1:8080", "http://127.1.2.3", "http://[::1]:8080",
            "http://[0:0:0:0:0:0:0:1]"})
    void loopbackCorsStopsContext(String origin) {
        assertStopped(prod().withPropertyValues("CORS_ALLOWED_ORIGINS=" + origin), "루프백");
    }

    @Test
    void secureOverrideStopsContext() {
        assertStopped(prod().withPropertyValues("CORS_ALLOWED_ORIGINS=" + ORIGIN,
                "auth.cookie.secure=false"), "secure=true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"newvent-dev-only-jwt-secret-do-not-use-in-prod",
            "test-only-not-a-real-secret-0123456789abcdef"})
    void developmentSecretStopsContext(String key) {
        assertStopped(runner("prod").withPropertyValues("JWT_SECRET=" + key,
                "CORS_ALLOWED_ORIGINS=" + ORIGIN), "개발·테스트용");
    }

    @Test
    void validProductionSettingsStartContext() {
        prod().withPropertyValues("CORS_ALLOWED_ORIGINS=" + ORIGIN + ",https://admin.example.com:8443")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(ProdAuthGuard.class));
                    assertTrue(context.getBean(AuthProps.class).cookie().secure());
                    assertEquals(2, context.getBean(AuthProps.class).cors().allowedOrigins().size());
                });
    }

    @Test
    void devDefaultsStillStartWithoutProductionGuard() {
        runner("dev").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(0, context.getBeansOfType(ProdAuthGuard.class).size());
            assertFalse(context.getBean(AuthProps.class).cookie().secure());
            assertTrue(context.getBean(AuthProps.class).cors().allowedOrigins().contains("http://localhost:5174"));
        });
    }
}
