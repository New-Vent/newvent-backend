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
 *
 * ★ 스모크 테스트는 여기를 안 거친다
 *   LlmSmokeTest 가 new OllamaClient(...) 로 직접 만든다. 스프링을 안 띄우려고 그렇게 했는데,
 *   그래서 **이 파일의 배선이 틀려도 테스트는 초록색**이다.
 *   실제로 ollama 프로파일이 죽는 걸 한동안 못 보고 있었다.
 */
@Configuration
@EnableConfigurationProperties(LlmProps.class)
public class LlmConfig {

    @Bean
    public LlmClient llmClient(LlmProps p) {
        return switch (p.provider()) {
            case "mock"   -> new MockLlmClient();
            case "ollama" -> new OllamaClient(p.baseUrl(), p.model(), p.timeoutSeconds());

            default -> throw new IllegalStateException(
                    "모르는 provider: " + p.provider() + " (mock | ollama)");
        };
    }
}