package com.newvent.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * OpenAPI 문서가 프로파일별로 켜지고 꺼지는지 고정한다. 스프링 컨텍스트 없이 돈다.
 *
 * 왜 테스트로 묶나 — 설정 한 줄이라 되돌리기 쉽고, 되돌아가도 로컬에서는 아무 증상이 없다.
 * 공개 서버에 Swagger UI 가 열린 것은 한참 뒤에나 알게 된다. 그래서 문장으로 박아 둔다.
 *
 * 환경변수를 뺀 채 바인딩한다 — "아무 환경변수 없이 뜨면 이 값이 나온다" 를 보려는 것이다
 * (AuthPropsTest 와 같은 방식).
 */
class OpenApiDocsProfileTest {

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

    @Test
    @DisplayName("기본(dev 포함): 문서가 켜져 있다 — 로컬에서 Swagger UI 로 본다")
    void 기본은_켜져_있다() throws IOException {
        var env = envWithoutSystem("application.yaml");

        assertEquals("true", env.getProperty("springdoc.api-docs.enabled"));
        assertEquals("true", env.getProperty("springdoc.swagger-ui.enabled"));
    }

    @Test
    @DisplayName("prod: 문서가 꺼져 있다 — 공개 서버에 엔드포인트 구조를 노출하지 않는다")
    void prod_는_꺼져_있다() throws IOException {
        var env = envWithoutSystem("application.yaml", "application-prod.yaml");

        assertEquals("false", env.getProperty("springdoc.api-docs.enabled"),
                "prod 에서 springdoc.api-docs.enabled 가 false 가 아닙니다. "
                        + "공개 서버에 OpenAPI 문서가 열립니다.");
        assertEquals("false", env.getProperty("springdoc.swagger-ui.enabled"),
                "prod 에서 springdoc.swagger-ui.enabled 가 false 가 아닙니다. "
                        + "공개 서버에 Swagger UI 가 열립니다.");
    }
}
