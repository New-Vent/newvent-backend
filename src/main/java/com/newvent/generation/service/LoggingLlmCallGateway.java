package com.newvent.generation.service;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Service;

import com.newvent.generation.domain.FailureType;
import com.newvent.infra.llm.LlmCallContext;
import com.newvent.infra.llm.LlmCallException;
import com.newvent.infra.llm.LlmCallGateway;
import com.newvent.infra.llm.LlmClient;

/**
 * 운영에서 쓰는 호출 문. 상한을 보고, 부르고, 호출 실패를 기록한다.
 */
@Service
public class LoggingLlmCallGateway implements LlmCallGateway {

    private final LlmClient llm;
    private final LlmCallLogService logs;
    private final LlmCallRecorder recorder;

    public LoggingLlmCallGateway(LlmClient llm, LlmCallLogService logs, LlmCallRecorder recorder) {
        this.llm = llm;
        this.logs = logs;
        this.recorder = recorder;
    }

    @Override
    public void reserve(int expectedCalls) {
        logs.checkDailyLimit(expectedCalls);
    }

    @Override
    public LlmClient.Response call(LlmCallContext ctx, LlmClient.Request req) {
        // ★ reserve 를 했어도 여기서 또 본다 — 확보와 실제 호출 사이에 시간이 있다
        logs.checkDailyLimit(1);
        try {
            return llm.chat(req);
        } catch (LlmCallException e) {
            // ★ 남기고 → 그대로 던진다. 삼키면 재시도 루프가 빈 응답으로 계속 돈다
            recorder.recordCallFailure(ctx, classify(e));
            throw e;
        }
    }

    /**
     * 타임아웃과 그 외를 가른다.
     *
     * ★ 완벽하지 않다. Bedrock 은 SDK 안에서 재시도를 다 하고 나서
     *   {@code ApiCallTimeoutException} 을 올리는데 그게 cause 사슬에 그대로 온다는 보장이 없다.
     *   못 가르면 LLM_ERROR 다 — 틀린 TIMEOUT 보다 낫다.
     */
    private static FailureType classify(LlmCallException e) {
        for (Throwable t = e.getCause(); t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof TimeoutException
                    || t instanceof SocketTimeoutException
                    || t instanceof HttpTimeoutException
                    || t.getClass().getSimpleName().contains("Timeout")) {
                return FailureType.TIMEOUT;
            }
        }
        return FailureType.LLM_ERROR;
    }
}
