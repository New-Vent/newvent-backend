package com.newvent.infra.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// devtools 가 붙어 있어서 개발 중 재시작마다 하나씩 쌓이지 않도록 interface LlmClient을 AutoCloseable로 변경
// 이후 `@Bean` 의 destroyMethod 추론이 실제로 걸리는지 테스트
class LlmClientLifecycleTest {

    static final AtomicInteger CLOSED = new AtomicInteger();

    // 닫히면 세는 것 말고는 아무것도 안 하는 클라이언트
    static class ProbeClient implements LlmClient {
        @Override
        public Response chat(Request r) {
            throw new UnsupportedOperationException("이 테스트는 호출하지 않는다");
        }

        @Override
        public String providerName() {
            return "probe";
        }

        @Override
        public String modelName() {
            return "probe";
        }

        @Override
        public void close() {
            CLOSED.incrementAndGet();
        }
    }

    @Configuration
    static class ProbeConfig {
        @Bean
        LlmClient llmClient() {
            return new ProbeClient();
        }
    }

    @Test
    @DisplayName("★ 컨텍스트를 내리면 스프링이 close() 를 부른다")
    void 컨텍스트가_닫히면_클라이언트도_닫힌다() {
        CLOSED.set(0);

        try (AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(ProbeConfig.class)) {
            assertNotNull(ctx.getBean(LlmClient.class));
            assertEquals(0, CLOSED.get(), "아직 닫히면 안 된다");
        }

        assertEquals(1, CLOSED.get(),
                "컨텍스트를 내렸는데 close() 가 안 불렸습니다. "
                + "LlmClient 가 AutoCloseable 인지, @Bean destroyMethod 추론이 "
                + "꺼져 있지 않은지 확인하세요.");
    }

    @Test
    @DisplayName("★ 진짜 BedrockClient 도 닫힌다 — 한 번도 안 쓴 클라이언트라도")
    void 베드락도_닫힌다() {
        // AWS SDK 는 자격증명을 호출 시점에 해결하므로 만들기·닫기는 키 없이 된다.
        LlmClient c = new LlmConfig()
                .llmClient(new LlmProps("bedrock", null, "google.gemma-3-27b-it",
                        null, 0, 0, 0));

        assertDoesNotThrow(c::close, "한 번도 호출 안 한 클라이언트를 닫다 터졌습니다");
        assertDoesNotThrow(c::close, "두 번 닫아도 터지면 안 됩니다");
    }

    @Test
    @DisplayName("닫을 게 없는 구현체는 기본 close() 로 조용히 지나간다")
    void 기본_close_는_아무것도_안_한다() {
        assertDoesNotThrow(() -> new MockLlmClient().close());
    }

    @Test
    @DisplayName("★ provider 와 model 이 따로 나온다 — 전환 전후 비교의 전제")
    void provider_와_model_이_나뉜다() {
        LlmClient mock = new MockLlmClient();
        assertEquals("mock", mock.providerName());
        assertEquals("mock", mock.modelName());

        LlmClient ollama = new OllamaClient("http://localhost:11434", "qwen2.5-coder:7b", 5);
        assertEquals("ollama", ollama.providerName(),
                "모델을 바꿔도 provider 는 같아야 합니다");
        assertEquals("qwen2.5-coder:7b", ollama.modelName());

        // 같은 provider, 다른 model — 이래야 전환 전후를 묶어서 볼 수 있다
        LlmClient ollama2 = new OllamaClient("http://localhost:11434", "exaone3.5:7.8b", 5);
        assertEquals(ollama.providerName(), ollama2.providerName());
        assertNotEquals(ollama.modelName(), ollama2.modelName());

        assertTrue(ollama.providerName().length() <= 40);
    }
}
