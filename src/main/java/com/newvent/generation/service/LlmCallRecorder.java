package com.newvent.generation.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.repository.EventRepository;
import com.newvent.generation.domain.FailureType;
import com.newvent.generation.domain.LlmCallLog;
import com.newvent.infra.llm.LlmCallContext;
import com.newvent.infra.llm.LlmClient;
import com.newvent.registry.BlockValidator.Failure;

/**
 * llm_call_logs 에 실제로 쓰는 층.
 *
 */
@Service
public class LlmCallRecorder {

    private static final Logger log = LoggerFactory.getLogger(LlmCallRecorder.class);

    private final Tx tx;

    public LlmCallRecorder(Tx tx) {
        this.tx = tx;
    }

    /** 재시도 묶음 전체 — 검증 통과·실패 행을 한 번에 */
    public void recordAttempts(LlmCallContext ctx, RetryService.Result result) {
        recordAttempts(ctx, result, null); 
    }

    /**
     * 재시도 묶음 전체 + RAG 사용 표시. chunkIds 가 null·blank 면 표시 없이 저장만 한다.
     */
    public void recordAttempts(LlmCallContext ctx, RetryService.Result result, String chunkIds) {
        if (!ctx.recordable() || result.traces().isEmpty()) return;
        try {
            tx.attempts(ctx, result, chunkIds);
        } catch (RuntimeException e) {
            warn(ctx, e);
        }
    }

    /** 호출 자체가 터진 시도 한 건 — Trace 가 없다 */
    public void recordCallFailure(LlmCallContext ctx, FailureType type) {
        if (!ctx.recordable()) return;
        try {
            tx.callFailure(ctx, type);
        } catch (RuntimeException e) {
            warn(ctx, e);
        }
    }

    /**
     * 재시도가 없는 단발 호출 한 건 — 라우터가 쓴다.
     *
     *     */
    public void recordSingle(LlmCallContext ctx, LlmClient.Response res, List<Failure> failures) {
        if (!ctx.recordable()) return;
        try {
            tx.single(ctx, res, failures);
        } catch (RuntimeException e) {
            warn(ctx, e);
        }
    }

    /**
     * 아무것도 남기지 않는 기록자. <b>테스트 전용.</b>
     *
     * ★ 빈이 아니다 — 운영에서는 쓰일 수 없다.
     */
    public static LlmCallRecorder none() {
        return new LlmCallRecorder(null) {
            @Override public void recordAttempts(LlmCallContext c, RetryService.Result r) { }
            @Override public void recordAttempts(LlmCallContext c, RetryService.Result r, String chunkIds) { }
            @Override public void recordCallFailure(LlmCallContext c, FailureType t) { }
            @Override public void recordSingle(LlmCallContext c, LlmClient.Response r, List<Failure> f) { }
        };
    }

    private static void warn(LlmCallContext ctx, RuntimeException e) {
        // ★ WARN 이다. 사용량 집계가 비는 건 조용히 지나가면 안 된다
        log.warn("호출 로그를 남기지 못했습니다 (event={}, request={}). "
                + "events 테이블에 그 이벤트 행이 없으면 FK 위반입니다 — 관리자 이벤트가 "
                + "아직 메모리 저장소에 있습니다.", ctx.eventId(), ctx.requestId(), e);
    }

    /**
     * 트랜잭션 경계만 담당한다. {@link LlmCallRecorder} 가 프록시를 타고 부른다.
     */
    @Component
    public static class Tx {

        private final EventRepository events;
        private final LlmCallLogService logs;
        private final LlmClient llm;

        public Tx(EventRepository events, LlmCallLogService logs, LlmClient llm) {
            this.events = events;
            this.logs = logs;
            this.llm = llm;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void attempts(LlmCallContext ctx, RetryService.Result result) {
            attempts(ctx, result, null);   // ★★★ 기존 본문 → 위임 1줄로 교체
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void attempts(LlmCallContext ctx, RetryService.Result result, String chunkIds) {
            List<LlmCallLog> rows = logs.record(result, event(ctx), null, ctx.requestId(), llm.modelName(), llm.providerName());
            if (chunkIds != null && !chunkIds.isBlank()) {
                rows.forEach(r -> r.markRagUsed(chunkIds));
            }
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void callFailure(LlmCallContext ctx, FailureType type) {
            logs.recordCallFailure(event(ctx), null, ctx.requestId(),
                    llm.modelName(), llm.providerName(), type, ctx.attemptNo());
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void single(LlmCallContext ctx, LlmClient.Response res, List<Failure> failures) {
            RetryService.Trace t = new RetryService.Trace(ctx.attemptNo(), res.content(),
                    List.copyOf(failures), res.inputTokens(), res.outputTokens(),
                    res.wallMs(), res.truncated());
            RetryService.Result one = new RetryService.Result(
                    failures.isEmpty(), res.content(), List.of(t));
            logs.record(one, event(ctx), null, ctx.requestId(), llm.modelName(), llm.providerName());
        }

        private Event event(LlmCallContext ctx) {
            return events.getReferenceById(ctx.eventId());
        }
    }
}
