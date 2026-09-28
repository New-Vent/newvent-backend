package com.newvent.editor.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.newvent.generation.service.LlmCallRecorder;
import com.newvent.infra.llm.LlmCallContext;
import com.newvent.infra.llm.LlmCallGateway;
import com.newvent.infra.llm.LlmClient;
import com.newvent.registry.BlockValidator.Failure;
import com.newvent.registry.PromptBuilder;

/**
 * 관리자 요청문을 연산 목록으로 바꾼다. **모델을 한 번 부른다.**
 *
 * ★ 이 한 번도 하루 상한과 로그에 잡힌다
 *   {@link LlmCallGateway} 를 통하므로 상한은 자동이다. 성공·실패 행은 여기서 남긴다
 *   ({@code RetryService} 를 안 쓰니 묶어 남겨 줄 사람이 없다).
 *
 * ★ 재시도가 없다
 *   못 알아들으면 관리자에게 다시 말해달라고 한다. 모델에게 다시 시키지 않는다.
 *   그래서 attemptNo 는 항상 1 이다.
 *
 * ★ LlmCallException 은 잡지 않는다
 *   호출 실패 행은 문이 이미 남겼다. 사용자 문구는 부르는 쪽(EditService) 몫이다.
 */
@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    /** 잘림 — done_reason 이라는 별개 사실. LlmCallLog.failureType 이 TRUNCATED 가 된다 */
    private static final Failure TRUNCATED =
            new Failure("truncated", "요청이 너무 복잡해 라우터 출력이 잘렸습니다.");

    /** 출력이 규격이 아니다 — VALIDATION_FAIL */
    private static final Failure UNPARSABLE =
            new Failure("router_parse", "라우터 출력이 규격에 맞지 않습니다.");

    private final LlmCallGateway gateway;
    private final LlmCallRecorder recorder;

    public RouteService(LlmCallGateway gateway, LlmCallRecorder recorder) {
        this.gateway = gateway;
        this.recorder = recorder;
    }

    /**
     * 못 알아들으면 빈 값. 호출부는 그걸 보고 관리자에게 다시 말해달라고 한다.
     *
     * @param ctx 이 호출을 붙일 이벤트·요청 꼬리표.
     *            {@code LlmCallContext.anonymous()} 를 주면 기록하지 않는다(테스트용)
     */
    public Optional<List<RawRoute>> route(LlmCallContext ctx, String requestText) {
        if (requestText == null || requestText.isBlank()) return Optional.empty();

        // ★ 여기서 예외가 나면 실패 행은 문이 남긴다. 아래는 안 돈다
        LlmClient.Response res = gateway.call(ctx,
                LlmClient.Request.router(PromptBuilder.router(), requestText.strip()));

        // ★ 잘린 출력은 파싱 전에 버린다.
        if (res.truncated()) {
            log.warn("라우터 출력이 잘렸습니다. 요청이 너무 복잡했을 수 있습니다.");
            recorder.recordSingle(ctx, res, List.of(TRUNCATED));
            return Optional.empty();
        }

        Optional<List<RawRoute>> ops = RouteParser.parse(res.content());
        if (ops.isEmpty()) {
            // ★ 원문을 로그에 남긴다.
            log.info("라우터 출력을 읽지 못했습니다: {}", brief(res.content()));
            recorder.recordSingle(ctx, res, List.of(UNPARSABLE));
            return ops;
        }

        recorder.recordSingle(ctx, res, List.of());
        return ops;
    }

    private static String brief(String s) {
        if (s == null) return "(없음)";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
