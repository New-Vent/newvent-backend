package com.newvent.infra.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * <b>진짜 application.yaml</b> 을 읽어서 LlmProps 에 붙여 본다. DB·네트워크 없음.
 *
 * ★ 왜 필요한가 — 여기는 컴파일러가 안 봐 주는 자리다
 *   yaml 키 이름과 record 컴포넌트가 어긋나도 **아무 경고 없이** 기본값이 들어간다.
 *   `daily-limit` 를 `dailyLimit` 로 적거나 `region` 을 `bedrock-region` 으로 적으면
 *   설정이 통째로 무시되는데, 앱은 멀쩡히 뜬다.
 *   조용히 깨지는 종류라 테스트로 고정한다.
 *
 * ★ 기본값은 yaml 이 아니라 LlmProps 가 갖는다
 *   yaml 은 `${LLM_MODEL:}` 처럼 **비워 두고**, 값이 없으면 LlmProps 의
 *   압축 생성자가 채운다. 두 곳에 같은 기본값을 적으면 언젠가 어긋난다.
 */
class LlmPropsBindingTest {

    /** 환경변수를 하나도 안 준 상태 — 팀원이 처음 클론했을 때와 같다 */
    private static LlmProps bind() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        loaded.forEach(env.getPropertySources()::addLast);

        // ★ PlaceholdersResolver 가 있어야 ${LLM_MODEL:} 가 풀린다.
        //   빼먹으면 값이 "${LLM_MODEL:}" 라는 **문자열 그대로** 바인딩된다.
        //   실제 앱에서는 Environment 가 알아서 풀어주지만 여기서는 직접 줘야 한다.
        return new Binder(ConfigurationPropertySources.get(env),
                new PropertySourcesPlaceholdersResolver(env))
                .bind("llm", LlmProps.class)
                .orElseThrow(() -> new AssertionError(
                        "llm.* 를 LlmProps 에 못 붙였습니다. yaml 키 이름을 확인하세요."));
    }

    @Test
    @DisplayName("★ yaml 의 llm.* 가 LlmProps 에 실제로 붙는다")
    void 바인딩된다() throws IOException {
        LlmProps p = bind();

        // 케밥케이스 → record 컴포넌트. 어긋나면 조용히 기본값이 들어간다.
        assertEquals(200, p.dailyLimit(), "llm.daily-limit 가 안 붙었습니다");
        assertEquals(3, p.maxRetry(), "llm.max-retry 가 안 붙었습니다");
        assertEquals(120, p.timeoutSeconds(), "llm.timeout-seconds 가 안 붙었습니다");
    }

    @Test
    @DisplayName("설정이 없어도 앱이 뜬다 — 기본값은 mock")
    void 기본값은_목() throws IOException {
        assertEquals("mock", bind().provider());
    }

    @Test
    @DisplayName("★ 비워 둔 값은 LlmProps 가 채운다 (yaml 에 기본값을 두 번 안 적는다)")
    void 빈값은_생성자가_채운다() throws IOException {
        LlmProps p = bind();

        // yaml 은 ${LLM_MODEL:} · ${BEDROCK_REGION:} 로 비어 있다.
        // 빈 문자열이 그대로 오면 LlmProps 가 안 채운 것이다.
        assertEquals("qwen2.5:7b", p.model(), "빈 model 을 LlmProps 가 안 채웠습니다");
        assertEquals(BedrockClient.DEFAULT_REGION, p.region(),
                "빈 region 을 LlmProps 가 안 채웠습니다");
    }

    @Test
    @DisplayName("★ 기본 설정으로 bedrock 을 켜면 기동에 실패한다 — model 을 안 바꿨으므로")
    void 기본값으로_베드락을_켜면_막힌다() throws IOException {
        LlmProps p = bind();
        LlmProps asBedrock = new LlmProps("bedrock", p.baseUrl(), p.model(), p.region(),
                p.dailyLimit(), p.maxRetry(), p.timeoutSeconds());

        // LLM_PROVIDER=bedrock 만 주고 LLM_MODEL 을 빼먹는 게 제일 흔한 실수다.
        // 그대로 두면 첫 호출에서 ValidationException 이 나는데 원인을 찾기 어렵다.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new LlmConfig().llmClient(asBedrock));
        assertTrue(e.getMessage().contains("qwen2.5:7b"), e.getMessage());
    }
}
