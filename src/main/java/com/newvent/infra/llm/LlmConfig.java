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

            //   model 기본값이 "qwen2.5:7b" 라 bedrock 에서는 반드시 덮어써야 한다.
            //   Bedrock 모델 id 를 안 주고 Ollama 이름으로 부르면
            //   ValidationException 이 나는데 원인을 찾기 어려워서 미리 체크함
            case "bedrock" -> {
                if (!BedrockClient.looksLikeModelId(p.model())) {
                    throw new IllegalStateException(
                            "llm.model 이 Bedrock 모델 id 가 아닙니다: " + p.model()
                            + " (예: google.gemma-3-27b-it). "
                            + "Anthropic·OpenAI 는 교차 리전 프로파일이라 us. 접두사가 붙습니다.");
                }
                yield new BedrockClient(p.region(), p.model(), p.timeoutSeconds());
            }

            default -> throw new IllegalStateException(
                    "모르는 provider: " + p.provider() + " (mock | ollama | bedrock)");
        };
    }
}
