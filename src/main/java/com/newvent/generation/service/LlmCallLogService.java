package com.newvent.generation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.common.error.LlmDailyLimitExceededException;
import com.newvent.generation.domain.LlmCallLog;
import com.newvent.generation.domain.LlmCallLog.FailureType;
import com.newvent.generation.repository.LlmCallLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그 기록·집계·상한 검사의 진입점. GenerationService 가 호출하는 쪽
 * 호출 순서 (GenerationService 담당자 확인 필요!)
 *  1. checkDailyLimit() 1회 -> 2. LLM 호출·재시도 -> 3. 시도(retry)마다 record() 1회
 *  record()는 DB 실패해도 예외를 삼켜서 본 플로우를 죽이지 않는다. (로그 때문에 이벤트 생성이 막히는 사태 방지)
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LlmCallLogService {

	private final LlmCallLogRepository repository;
	private final Clock clock;
	
	/**
	 * 호출 1건을 기록 -> 성공·실패·연결 실패 모두 이 메서드로 남김
	 * failCodes 가 비어 있으면 null 로 저장 ( 빈 문자열과 null 구분 목적 : null = 실패 상세 없음, 값 있음 = 상세 있음 )
	 */
	@Transactional
	public void record(Long eventId, Long versionId, int attemptNo, String modelName,
			int inputTokens, int outputTokens, Integer wallMs,
			boolean success, FailureType failureType, List<String> failCodes) {
		try {
			String joined = (failCodes == null || failCodes.isEmpty()) ? null : String.join("|", failCodes);
			LlmCallLog log = LlmCallLog.create(eventId, versionId, attemptNo, modelName, 
					inputTokens, outputTokens, wallMs, 
					success, failureType, joined, Instant.now(clock));
			repository.save(log);
		} catch(Exception e) {
			// 자가 방어 : 저장 실패 (DB 다운 등) 가 GenerationService 로 번지지 않게 여기서 끝냄
			log.warn("LLM 호출 로그 저장 실패 : eventId = {}", eventId, e);
		}
	}

	// 오늘(KST) 호출 건수 -> 자정 경계는 Clock 기준이라 테스트에서 고정 시간으로 검증 레포지토리는 범위만 받고, 범위 계산 책임은 여기 있음
	public long countToday() {
		LocalDate today = LocalDate.now(clock);
		ZoneId zone = clock.getZone();
		Instant start = today.atStartOfDay(zone).toInstant();
		Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
		return repository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end);
	}
	
	// 요청 시작 시 1회 호출 -> 재시도 시도마다 row 가 쌓이므로 과금 기준은 호출
	// -> 초과 시 LlmDailyLimitExceededException -> 429 매핑 예정 (GlobalExceptionHandler 위치 결정 후)
	public void checkDailyLimit(int dailyLimit) {
		long used = countToday();
		if (used >= dailyLimit) {
			throw new LlmDailyLimitExceededException(dailyLimit, used);
		}
	}
	
	// 주 N 주치 사용량 -> UsageController (나중) 호출 예정. from 이전 데이터는 읽지 않음.
	public List<LlmCallLogRepository.WeeklyUsageRow> weeklyUsage(Instant from){
		return repository.sumTokensByWeek(from);
	}
}
