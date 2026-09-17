package com.newvent.infra.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * llm.* 설정을 담는다.
 *
 * ★ 기본값이 mock 인 게 핵심이다.
 *   설정 파일이 없거나 값을 안 적어도 앱이 뜬다.
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProps(
        String provider,
        String baseUrl,
        String model,

        /** 하루 최대 호출 수. 무한루프 방어의 마지막 선 */
        int dailyLimit,

        /** 재시도 상한. 되먹임을 줘도 이 횟수를 넘기지 않는다 */
        int maxRetry,

        /** 호출 타임아웃(초). 7B 생성은 10초를 넘긴다 */
        int timeoutSeconds
) {
    public LlmProps {
        if (provider == null || provider.isBlank()) provider = "mock";
        if (baseUrl  == null || baseUrl.isBlank())  baseUrl  = "http://localhost:11434";
        if (model    == null || model.isBlank())    model    = "qwen2.5:7b";
        if (dailyLimit     <= 0) dailyLimit     = 200;
        if (maxRetry       <= 0) maxRetry       = 3;
        if (timeoutSeconds <= 0) timeoutSeconds = 120;
    }
}
