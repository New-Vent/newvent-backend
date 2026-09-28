package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.generation.domain.FailureType;
import com.newvent.generation.exception.LlmDailyLimitExceededException;
import com.newvent.infra.llm.LlmCallContext;
import com.newvent.infra.llm.LlmCallException;
import com.newvent.infra.llm.LlmCallGateway;
import com.newvent.infra.llm.LlmClient;

/**
 * 호출 경계 단위 테스트 — 스프링 컨텍스트 · DB 를 띄우지 않는다.
 *
 * 여기서 지키는 것 셋:
 *   ① 상한을 넘으면 **모델을 아예 안 부른다** (부르고 나서 거절하면 돈이 이미 나갔다)
 *   ② 호출이 터지면 실패 행을 남기고 **그대로 다시 던진다**
 *   ③ 성공은 여기서 기록하지 않는다 — 검증 결과를 아직 모른다
 */
class LlmCallGatewayTest {

    private static final LlmCallContext CTX = LlmCallContext.of(7L, UUID.randomUUID());
    private static final LlmClient.Request REQ = LlmClient.Request.html("sys", "user");

    private final LlmClient llm = mock(LlmClient.class);
    private final LlmCallLogService logs = mock(LlmCallLogService.class);
    private final LlmCallRecorder recorder = mock(LlmCallRecorder.class);

    private LoggingLlmCallGateway gateway() {
        return new LoggingLlmCallGateway(llm, logs, recorder);
    }

    // ── ① 상한 ────────────────────────────────────────────────────

    @Test
    @DisplayName("상한을 넘으면 모델을 부르지 않는다.")
    void 상한_초과시_호출_안함() {
        doThrow(new LlmDailyLimitExceededException(200, 200))
                .when(logs).checkDailyLimit(1);

        assertThrows(LlmDailyLimitExceededException.class, () -> gateway().call(CTX, REQ));

        verifyNoInteractions(llm);                       // ★ 돈이 나가지 않았다
        verify(recorder, never()).recordCallFailure(any(), any());
    }

    @Test
    @DisplayName("reserve 는 재시도 묶음 몫을 한 번에 확보한다.")
    void reserve_는_묶음_몫을_확보한다() {
        gateway().reserve(4);
        verify(logs).checkDailyLimit(4);
    }

    @Test
    @DisplayName("RetryService.reserve() 는 최대 시도 횟수만큼 확보한다.")
    void retryService_reserve_는_maxAttempts_만큼() {
        LlmCallGateway g = mock(LlmCallGateway.class);
        new RetryService(g, 3).reserve();
        verify(g).reserve(4);                            // 최초 1 + 재시도 3
    }

    // ── ② 호출 실패 ───────────────────────────────────────────────

    @Test
    @DisplayName("호출이 터지면 실패 행을 남기고 그대로 다시 던진다.")
    void 호출_실패는_기록하고_다시_던진다() {
        LlmCallException boom = new LlmCallException("모델 서버에 연결하지 못했습니다.");
        when(llm.chat(any())).thenThrow(boom);

        LlmCallException thrown = assertThrows(LlmCallException.class,
                () -> gateway().call(CTX.attempt(2), REQ));

        assertSame(boom, thrown);                        // ★ 삼키거나 바꿔치지 않는다
        verify(recorder).recordCallFailure(eq(CTX.attempt(2)), eq(FailureType.LLM_ERROR));
    }

    @Test
    @DisplayName("타임아웃 원인이 사슬에 있으면 TIMEOUT 으로 분류한다.")
    void 타임아웃은_TIMEOUT() {
        when(llm.chat(any())).thenThrow(
                new LlmCallException("응답이 없습니다.", new SocketTimeoutException("read timed out")));

        assertThrows(LlmCallException.class, () -> gateway().call(CTX, REQ));

        verify(recorder).recordCallFailure(any(), eq(FailureType.TIMEOUT));
    }

    // ── ③ 성공 ────────────────────────────────────────────────────

    @Test
    @DisplayName("성공한 호출은 경계에서 기록하지 않는다 — 검증 결과를 아직 모른다.")
    void 성공은_경계에서_기록하지_않는다() {
        LlmClient.Response res = new LlmClient.Response("<div></div>", 10, 20, 5L, false);
        when(llm.chat(any())).thenReturn(res);

        assertSame(res, gateway().call(CTX, REQ));

        verify(recorder, never()).recordCallFailure(any(), any());
        verify(recorder, never()).recordAttempts(any(), any());
    }

    // ── 끊긴 재시도 ───────────────────────────────────────────────

    @Test
    @DisplayName("3차에서 호출이 터져도 1·2차 시도 기록은 살아서 나온다.")
    void 호출_실패시_이전_시도가_실려_나온다() {
        // 1·2차 — 검증에 걸릴 쓰레기 출력. 3차 — 연결이 끊긴다
        LlmClient dying = new LlmClient() {
            private int n = 0;

            @Override
            public Response chat(Request request) {
                if (++n >= 3) throw new LlmCallException("연결이 끊겼습니다.");
                return new Response("<div>아무것도 아님</div>", 10, 20, 5L, false);
            }

            @Override public String providerName() { return "fake"; }
            @Override public String modelName() { return "fake-model"; }
        };

        RetryService.Aborted e = assertThrows(RetryService.Aborted.class,
                () -> new RetryService(dying, 3).run(CTX, "sys", "여름 이벤트", HtmlPolicy.generation()));

        assertEquals(3, e.attempt());
        assertEquals(2, e.traces().size());              // ★ 1·2차가 살아 있다
        assertEquals(List.of(1, 2), e.traces().stream().map(RetryService.Trace::attempt).toList());
        assertTrue(e.partial().traces().size() == 2);
        assertTrue(e.getCause() instanceof LlmCallException);
    }

    @Test
    @DisplayName("1차에서 터지면 실린 시도가 없다 — 실패 행은 경계가 이미 남겼다.")
    void 첫_시도에서_터지면_실린_것이_없다() {
        LlmClient dead = new LlmClient() {
            @Override public Response chat(Request request) {
                throw new LlmCallException("연결이 끊겼습니다.");
            }

            @Override public String providerName() { return "fake"; }
            @Override public String modelName() { return "fake-model"; }
        };

        RetryService.Aborted e = assertThrows(RetryService.Aborted.class,
                () -> new RetryService(dead, 3).run(CTX, "sys", "여름 이벤트", HtmlPolicy.generation()));

        assertEquals(1, e.attempt());
        assertTrue(e.traces().isEmpty());
    }
}
