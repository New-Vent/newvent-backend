package com.newvent.infra.llm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배선이 맞는지 확인하는 테스트. **AWS 자격증명도 네트워크도 필요 없다 — CI 에서 돈다.**
 *
 * ★ 왜 이 파일이 필요한가
 *   LlmSmokeTest 는 구현체를 `new BedrockClient(...)` 로 **직접** 만든다.
 *   스프링을 안 띄우려고 그렇게 했는데, 그래서 **LlmConfig 가 틀려도 스모크는 초록색**이다.
 *   실제로 그 틈 때문에 ollama 프로파일이 한동안 죽어 있는 걸 못 봤다.
 *   여기가 그 구멍을 막는다.
 *
 * ★ Bedrock 호출은 하지 않는다
 *   AWS SDK v2 는 자격증명을 **호출 시점**에 해결한다. 클라이언트를 만드는 것만으로는
 *   네트워크도 키도 안 쓴다. 그래서 배선만 0원으로 확인할 수 있다.
 */
class LlmConfigTest {

    private static LlmProps props(String provider, String model) {
        return new LlmProps(provider, null, model, null, 0, 0, 0);
    }

    @Test
    @DisplayName("provider=bedrock 이면 BedrockClient 가 나온다")
    void 베드락_배선() {
        LlmClient c = new LlmConfig().llmClient(props("bedrock", "google.gemma-3-27b-it"));

        assertInstanceOf(BedrockClient.class, c);
        assertEquals("bedrock", c.providerName());
    }

    @Test
    @DisplayName("★ providerName 이 llm_call_logs.provider(VARCHAR 20) 에 들어간다")
    void provider_컬럼_길이() {
        LlmClient c = new LlmConfig().llmClient(props("bedrock", "google.gemma-3-27b-it"));

        // ★ 여기서 모델명까지 붙이면 38자가 되어 저장이 터진다.
        //   모델은 같은 테이블 model_name(VARCHAR 100) 에 따로 들어간다.
        assertTrue(c.providerName().length() <= 20,
                "provider 가 20자를 넘습니다: " + c.providerName());
    }

    @Test
    @DisplayName("★ Ollama 모델 이름으로 bedrock 을 부르면 그 자리에서 막는다")
    void 모델id_형식이_틀리면_기동에_실패한다() {
        // llm.model 기본값이 qwen2.5:7b 라 덮어쓰는 걸 빼먹기 쉽다.
        // 그대로 두면 첫 호출에서 ValidationException 이 나는데 원인을 찾기 어렵다.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new LlmConfig().llmClient(props("bedrock", "qwen2.5:7b")));

        assertTrue(e.getMessage().contains("qwen2.5:7b"), e.getMessage());
        assertTrue(e.getMessage().contains("us."),
                "교차 리전 프로파일 접두사 안내가 있어야 합니다: " + e.getMessage());
    }

    @Test
    @DisplayName("리전 기본값은 서울이 아니다 — 서울엔 Anthropic 모델이 없다")
    void 리전_기본값() {
        assertEquals("us-east-1", BedrockClient.DEFAULT_REGION);
        assertEquals("us-east-1", props("bedrock", "google.gemma-3-27b-it").region());
    }

    @Test
    @DisplayName("★ 모델 id 판별 — 점만 보면 못 가른다")
    void 모델id_판별() {
        // Bedrock — 벤더 접두사가 영문자
        assertTrue(BedrockClient.looksLikeModelId("google.gemma-3-27b-it"));
        assertTrue(BedrockClient.looksLikeModelId("qwen.qwen3-coder-30b-a3b-v1:0"));
        assertTrue(BedrockClient.looksLikeModelId("openai.gpt-oss-120b-1:0"));
        // 교차 리전 추론 프로파일 — Anthropic·OpenAI 는 us. 가 붙는다
        assertTrue(BedrockClient.looksLikeModelId("us.anthropic.claude-haiku-4-5-20251001-v1:0"));

        // Ollama — 접두사에 숫자가 있다. ★ 셋 다 점이 있어서 "점이 있나" 로는 못 가른다
        assertFalse(BedrockClient.looksLikeModelId("qwen2.5:7b"));
        assertFalse(BedrockClient.looksLikeModelId("exaone3.5:7.8b"));
        assertFalse(BedrockClient.looksLikeModelId("gemma3:4b"));

        assertFalse(BedrockClient.looksLikeModelId(null));
        assertFalse(BedrockClient.looksLikeModelId(""));
    }

    @Test
    @DisplayName("모르는 provider 는 이름을 알려주며 죽는다")
    void 모르는_provider() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new LlmConfig().llmClient(props("gpt", null)));

        assertTrue(e.getMessage().contains("bedrock"),
                "선택지 목록에 bedrock 이 있어야 합니다: " + e.getMessage());
    }

    @Test
    @DisplayName("기본값은 여전히 mock — 설정이 없어도 앱이 뜬다")
    void 기본값은_목() {
        assertInstanceOf(MockLlmClient.class,
                new LlmConfig().llmClient(props(null, null)));
    }
}
