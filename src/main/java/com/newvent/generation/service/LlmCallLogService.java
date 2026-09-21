package com.newvent.generation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.newvent.common.error.LlmDailyLimitExceededException;
import com.newvent.generation.domain.LlmCallLog;
import com.newvent.generation.repository.LlmCallLogRepository;
import com.newvent.infra.llm.LlmProps;
import com.newvent.registry.BlockValidator.Failure;

/**
 * llm_call_logs 쓰기 전담
 */
@Service
public class LlmCallLogService {

	private final LlmCallLogRepository logs;
	private final Clock clock;
	private final int dailyLimit;
	
	@Autowired
	public LlmCallLogService(LlmCallLogRepository logs, Clock clock, LlmProps props) {
		this(logs, clock, props.dailyLimit());
	}
	
	/** 테스트용 — 일일 상한을 직접 준다 */
    LlmCallLogService(LlmCallLogRepository logs, Clock clock, int dailyLimit) {
        this.logs = logs;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
    }

    // 매핑 규칙 (한 곳)

    /** Trace 한 건 -> 로그 한 행 */
    static LlmCallLog recordToEntity(RetryService.Trace t, long eventId, Long versionId,
            String modelName, Instant createdAt) {
        boolean success = t.passed();
        LlmCallLog.FailureType type = success ? null : failureTypeOf(t);
        String codes = success ? null : t.failures().stream()
                .map(Failure::code)
                .collect(Collectors.joining("|"));
        return LlmCallLog.create(eventId, versionId, t.attempt(), modelName,
                t.inputTokens(), t.outputTokens(), (int) t.wallMs(),
                success, type, codes, createdAt);
    }

    /** TRUNCATED 우선 — 잘림은 done_reason 이라는 별개 사실이라 검증 실패보다 위 */
    static LlmCallLog.FailureType failureTypeOf(RetryService.Trace t) {
        boolean truncated = t.truncated()
                || t.failures().stream().anyMatch(f -> f.code().equals("truncated"));
        return truncated ? LlmCallLog.FailureType.TRUNCATED
                         : LlmCallLog.FailureType.VALIDATION_FAIL;
    }

    // 쓰기

    /** 재시도 전체 결과를 행으로 떨군다. 호출 전에 checkDailyLimit 를 먼저 부른다. */
    public List<LlmCallLog> record(RetryService.Result result, long eventId,
            Long versionId, String modelName) {
        List<LlmCallLog> rows = new ArrayList<>();
        Instant at = clock.instant();
        int last = result.traces().size();
        int i = 0;
        for (RetryService.Trace t : result.traces()) {
            i++;
            // versionId 는 "저장까지 이어진" 마지막 성공 시도에만.
            // 선행 실패 시도·실패 결과는 무조건 null
            Long vid = (result.ok() && i == last) ? versionId : null;
            rows.add(recordToEntity(t, eventId, vid, modelName, at));
        }
        return logs.saveAll(rows);
    }

    /**
     * 호출 자체 실패(연결 끊김·타임아웃·500) — Trace 가 없어서 별도 기록.
     * 타입 분류는 부르는 쪽(GenerationService) 정책이다. 여기는 받아서 저장만 한다.
     * 토큰은 0(측정 못 함), 응답시간은 null.
     */
    public LlmCallLog recordCallFailure(long eventId, Long versionId, String modelName,
            LlmCallLog.FailureType type) {
        LlmCallLog row = LlmCallLog.create(eventId, versionId, 1, modelName,
                0, 0, null, false, type, null, clock.instant());
        return logs.save(row);
    }

    // 일일 상한

    /** 오늘 사용량(전역 합산). KST 자정 경계 계산은 여기서 한다. */
    long usedToday() {
        ZoneId zone = clock.getZone();
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        return logs.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end);
    }

    /** 호출 전 방어선 — used >= dailyLimit 이면 거부 (상한 직전까지 허용) */
    public void checkDailyLimit() {
        long used = usedToday();
        if (used >= dailyLimit) {
            throw new LlmDailyLimitExceededException(dailyLimit, used);
        }
    }
}
