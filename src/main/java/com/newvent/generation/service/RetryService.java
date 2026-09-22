package com.newvent.generation.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
 * ★ LlmCallException 은 잡지 않는다
 *   연결 끊김 · 타임아웃 · 500 은 프롬프트를 고쳐도 안 고쳐진다.
 *   그대로 위로 던진다. 여기서 4번 기다리면 Ollama 가 꺼졌을 때 4배로 죽는다.
 *
 * ★ 다 실패하면 ok=false 로 끝난다. 여기서 기본 틀을 끼워 넣지 않는다.
 *   실패를 완료로 포장하면 관리자가 자기 요청이 반영된 줄 알고 게시한다.
 */
@Service
public class RetryService {

    private final LlmClient llm;
    private final int maxRetry;

    @Autowired
    public RetryService(LlmClient llm, LlmProps props) {
        this(llm, props.maxRetry());
    }

    /** 테스트용 — 재시도 횟수를 직접 준다 */
    RetryService(LlmClient llm, int maxRetry) {
        this.llm = llm;
        this.maxRetry = maxRetry;
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
            return failures.isEmpty();
        }
    }

    /** html 은 ok == true 일 때만 값이 있다. */
    public record Result(boolean ok, String html, List<Trace> traces) {

        public int attempts() {
            return traces.size();
        }

        public List<Failure> lastFailures() {
            return traces.isEmpty() ? List.of() : traces.get(traces.size() - 1).failures();
        }
    }

    /**
     * 최초 1회 + 재시도 maxRetry 회. 기본값이면 최대 4회다.
     *
     *
     *     */
    public Result run(String system, String user, HtmlPolicy policy) {
        List<Trace> traces = new ArrayList<>();
        String nextUser = user;

        for (int attempt = 1; attempt <= maxRetry + 1; attempt++) {
            // ★ 시간은 LlmClient 가 이미 재서 Response.wallMs 로 준다. 여기서 또 재지 않는다.
            LlmClient.Response res = llm.chat(LlmClient.Request.html(system, nextUser));

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

            if (fails.isEmpty()) {
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
            problems.append("- ").append(f.message()).append('\n');
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
