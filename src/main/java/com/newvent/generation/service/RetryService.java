package com.newvent.generation.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.newvent.infra.llm.LlmCallContext;
import com.newvent.infra.llm.LlmCallException;
import com.newvent.infra.llm.LlmCallGateway;
import com.newvent.infra.llm.LlmClient;
import com.newvent.infra.llm.LlmProps;
import com.newvent.registry.BlockValidator.Failure;

/**
 * 검증을 통과한 HTML 이 나올 때까지 모델을 다시 부른다. — REQ-LLM-42, REQ-LLM-43
 *
 * 하는 일은 하나다 — "유효한 HTML 한 덩어리". 그 외는 안 한다:
 *   저장 · 로그 적재 · merge 는 전부 부르는 쪽(GenerationService / editor) 몫이다.
 *
 * ★ 이 파일에 규칙이 없다
 *   무엇을 지우고 무엇을 합격으로 볼지는 전부 HtmlPolicy 가 갖고 있다.
 *   여기에 "h1 이 있나" 나 "data-slot 을 지운다" 를 쓰지 말 것.
 *   규칙이 두 군데가 되는 순간 어긋난다.
 *
 * ★ 재시도가 작동하는 이유
 *   Failure.message 는 "hero 영역이 없습니다" 같은 **의미** 메시지다.
 *   "JSON 파싱 실패" 같은 문법 메시지를 주면 모델이 못 고친다. 벤치마크에서 확인한 것.
 *
 * ★ 대화 이력은 쌓지 않는다. 직전 출력 하나만 들고 간다.
 *   3차 시도의 입력 = 원래 요청 + 2차 출력 + 2차 실패 메시지. 1차 흔적은 없다.
 *   이력 누적이 실패를 증폭시키는 건 벤치마크 C군에서 확인했다.
 *   다만 "직전 출력 1개를 넣는 것" 자체는 아직 측정 전이다 — retryPrompt() 만 고치면 바뀐다.
 *
 * ★ 모델을 직접 부르지 않는다 — {@link LlmCallGateway} 를 통한다
 *   문이 하루 상한을 보고, 호출이 터지면 실패 행을 남긴다.
 *   여기서 {@link LlmClient} 를 다시 들면 그 호출은 상한에도 로그에도 안 잡힌다.
 *
 * ★ LlmCallException 은 잡아서 고치지 않는다 — {@link Aborted} 로 바꿔 던진다
 *   연결 끊김 · 타임아웃 · 500 은 프롬프트를 고쳐도 안 고쳐진다. 재시도하지 않는다.
 *   바꿔 던지는 이유는 하나다 — <b>여태 모은 시도 기록을 들고 나가야 한다.</b>
 *   3차에서 연결이 끊겼을 때 그냥 던지면 1·2차의 실패 행(토큰·응답시간 포함)이 통째로 사라진다.
 *   {@code Aborted} 는 {@code LlmCallException} 을 상속하므로 기존 catch 는 그대로 걸린다.
 *
 * ★ 다 실패하면 ok=false 로 끝난다. 여기서 기본 틀을 끼워 넣지 않는다.
 *   실패를 완료로 포장하면 관리자가 자기 요청이 반영된 줄 알고 게시한다.
 */
@Service
public class RetryService {

    private final LlmCallGateway gateway;
    private final int maxRetry;

    @Autowired
    public RetryService(LlmCallGateway gateway, LlmProps props) {
        this(gateway, props.maxRetry());
    }

    /** 테스트용 — 재시도 횟수를 직접 준다 */
    RetryService(LlmCallGateway gateway, int maxRetry) {
        this.gateway = gateway;
        this.maxRetry = maxRetry;
    }

    /**
     * 테스트용 — 상한도 기록도 없이 모델만 부른다.
     *
     * ★ 운영 경로에서 쓰지 말 것. {@link LlmCallGateway.Direct} 와 같은 이유다.
     */
    RetryService(LlmClient llm, int maxRetry) {
        this(new LlmCallGateway.Direct(llm), maxRetry);
    }

    /** 최초 1회 + 재시도 maxRetry 회. 기본값이면 4 */
    public int maxAttempts() {
        return maxRetry + 1;
    }

    /**
     * 이 묶음이 쓸 호출 수를 미리 확보한다. <b>동기 구간에서 부른다.</b>
     *
     * ★ 작업 스레드에 넘긴 뒤에 보면 429 가 관리자에게 도달하지 못한다.
     *
     * 상한을 넘으면 {@code LlmDailyLimitExceededException} — 429 로 나간다.
     */
    public void reserve() {
        gateway.reserve(maxAttempts());
    }

    /**
     * 시도 한 번의 기록. 그대로 LlmCallLog 재료가 된다.
     *
     * raw 는 모델 원문(코드펜스·설명문 포함). 정화 전이다.
     * 성공한 시도의 raw 는 저장하지 않고 version 을 참조하면 된다 — REQ-LLM-69.
     */
    public record Trace(
            int attempt,
            String raw,
            List<Failure> failures,
            int inputTokens,
            int outputTokens,
            long wallMs,
            boolean truncated) {

        public boolean passed() {
            return failures.stream().noneMatch(Failure::isBlocking);
        }
    }

    /** html 은 ok == true 일 때만 값이 있다. */
    public record Result(boolean ok, String html, List<Trace> traces) {

        public int attempts() {
            return traces.size();
        }

        public List<Failure> lastFailures() {
            return traces.isEmpty() ? List.of() : traces.get(traces.size() - 1).failures().stream()
                    .filter(Failure::isBlocking).toList();
        }

        /** 마지막 시도의 경고. 성공 결과에서도 관리자에게 안내할 수 있다. */
        public List<Failure> lastWarnings() {
            return traces.isEmpty() ? List.of() : traces.get(traces.size() - 1).failures().stream()
                    .filter(Failure::isWarning).toList();
        }
    }

    /**
     * 호출이 터져서 루프를 못 이어갔다. <b>여태 모은 시도 기록을 들고 나간다.</b>
     *
     * ★ {@code LlmCallException} 을 상속한다 — 부르는 쪽의 기존 catch 가 그대로 걸린다.
     *   따로 기록할 게 있는 쪽만 {@code instanceof Aborted} 로 한 줄 더 본다.
     *
     * ★ {@link #attempt()} 시도의 실패 행은 이미 {@link LlmCallGateway} 가 남겼다.
     *   여기 담긴 {@link #partial()} 은 그 <b>앞의</b> 시도들이다. attempt_no 가 겹치지 않는다.
     */
    public static class Aborted extends LlmCallException {

        private static final long serialVersionUID = 1L;

        private final int attempt;
        private final transient List<Trace> traces;

        Aborted(int attempt, List<Trace> traces, LlmCallException cause) {
            super(cause.getMessage(), cause);
            this.attempt = attempt;
            this.traces = traces;
        }

        /** 몇 번째 시도에서 터졌는가 */
        public int attempt() {
            return attempt;
        }

        /** 터지기 전까지의 시도들. 없을 수도 있다(1차에서 터진 경우) */
        public List<Trace> traces() {
            return traces;
        }

        /** 그 시도들만 담은 결과. 성공이 아니므로 ok=false — version 이 안 붙는다 */
        public Result partial() {
            return new Result(false, null, traces);
        }
    }

    /**
     * 기록 없이 돌린다. <b>테스트 전용</b> — 붙일 이벤트가 없다.
     */
    public Result run(String system, String user, HtmlPolicy policy) {
        return run(LlmCallContext.anonymous(), system, user, policy);
    }

    /**
     * 최초 1회 + 재시도 maxRetry 회. 기본값이면 최대 4회다.
     *
     * @param ctx 이 묶음의 꼬리표. attemptNo 는 여기서 시도마다 갈아 준다
     * @throws Aborted 호출이 터졌을 때 — 여태 모은 시도가 실려 있다
     */
    public Result run(LlmCallContext ctx, String system, String user, HtmlPolicy policy) {
        List<Trace> traces = new ArrayList<>();
        String nextUser = user;

        for (int attempt = 1; attempt <= maxAttempts(); attempt++) {
            // ★ 시간은 LlmClient 가 이미 재서 Response.wallMs 로 준다. 여기서 또 재지 않는다.
            LlmClient.Response res;
            try {
                res = gateway.call(ctx.attempt(attempt),
                        LlmClient.Request.html(system, nextUser));
            } catch (Aborted e) {
                throw e;                      // 이미 실려 있다. 두 번 감싸지 않는다
            } catch (LlmCallException e) {
                // ★ 재시도하지 않는다. 다만 1..attempt-1 을 버리지 않는다
                throw new Aborted(attempt, List.copyOf(traces), e);
            }

            // ★ 순서: clean → validate
            //   정화를 검증보다 먼저 건다. 최종 산출물이 검증을 통과했음을 보장해야 하기 때문이다.
            //   반대로 하면 "검증은 통과했는데 정화가 깨뜨린 HTML" 이 나간다.
            String html = policy.clean(res.content());
            List<Failure> fails = new ArrayList<>(policy.validate(html));

            // ★ 잘림은 검증으로 안 잡힌다.
            //   중간에 끊겨도 Jsoup 이 태그를 자동으로 닫아버려서 검증은 통과할 수 있다.
            //   done_reason == "length" 를 의미 메시지로 바꿔서 강제로 재시도시킨다.
            if (res.truncated()) {
                fails.add(0, new Failure("truncated",
                        "출력이 너무 길어 중간에 잘렸습니다. 항목 수와 문장을 줄여 더 짧게 만드세요."));
            }

            traces.add(new Trace(attempt, res.content(), List.copyOf(fails),
                    res.inputTokens(), res.outputTokens(), res.wallMs(), res.truncated()));

            if (fails.stream().noneMatch(Failure::isBlocking)) {
                return new Result(true, html, List.copyOf(traces));
            }
            nextUser = retryPrompt(user, html, fails);
        }
        return new Result(false, null, List.copyOf(traces));
    }

    /**
     * 재시도 프롬프트.
     *
     * ★ originalUser 를 매번 새로 붙인다. 직전 재시도 프롬프트를 쌓지 않는다.
     *   그래서 3차 시도의 입력에 1차 출력이 들어가지 않는다.
     */
    private String retryPrompt(String originalUser, String lastHtml, List<Failure> fails) {
        StringBuilder problems = new StringBuilder();
        for (Failure f : fails) {
            if (f.isBlocking()) {
                problems.append("- ").append(f.message()).append('\n');
            }
        }
        return """
                %s

                ────────────────
                방금 만든 결과에 문제가 있습니다.

                [문제]
                %s
                [방금 만든 것]
                %s

                위 문제만 고쳐서 전체를 다시 출력하세요.
                문제없는 부분은 그대로 두세요. 설명은 쓰지 마세요."""
                .formatted(originalUser, problems, lastHtml);
    }
}
