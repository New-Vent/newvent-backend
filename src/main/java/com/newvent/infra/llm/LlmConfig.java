package com.newvent.infra.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LlmClient 구현체를 하나 고른다. **여기가 유일한 분기점이다.**
 *
 * generation · editor 패키지는 LlmClient 인터페이스만 본다.
 * 로컬이든 목이든 호출하는 쪽 코드는 한 줄도 안 바뀐다.
 *
 * ★ 이 파일은 registry 를 모른다. 알 필요가 없다.
 *   레지스트리 정합성 검사는 RegistryConfig 가 한다.
 */
@Configuration
@EnableConfigurationProperties(LlmProps.class)
public class LlmConfig {

    @Bean
    public LlmClient llmClient(LlmProps p) {
        return switch (p.provider()) {
            case "mock"   -> new MockLlmClient();

            // ── OllamaClient 를 만들면 아래 한 줄만 주석 해제 ──
            // case "ollama" -> new OllamaClient(p.baseUrl(), p.model(), p.timeoutSeconds());
            case "ollama" -> throw new IllegalStateException(
                    "OllamaClient 가 아직 없습니다. llm.provider 를 mock 으로 두세요.");

            default -> throw new IllegalStateException(
                    "모르는 provider: " + p.provider() + " (mock | ollama)");
        };
    }
}
