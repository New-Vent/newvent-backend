package com.newvent.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.newvent.common.error.LlmDailyLimitExceededException;
import com.newvent.generation.domain.LlmCallLog;
import com.newvent.generation.repository.LlmCallLogRepository;
import com.newvent.registry.BlockValidator.Failure;

// LlmCallLogService 단위 테스트 - 스프링 컨텍스트 · DB 를 띄우지 않는다.
@ExtendWith(MockitoExtension.class)
public class LlmCallLogServiceTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	// 2026-09-21 12:00 KST - 자정 경계로부터 멀어 재현이 일정
	private static final Instant NOW = Instant.parse("2026-09-21T03:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, KST);
	private static final int LIMIT = 3;
	
	@Mock
	private LlmCallLogRepository logs;
	
	private LlmCallLogService newService() {
		return new LlmCallLogService(logs, CLOCK, LIMIT);
	}
	
	// ---------------- 매핑 규칙 ------------------
	@Test
	@DisplayName("통과 시도는 success=true, 실패 정보 없이 기록된다.")
	void 통과_시도_success로_기록() {
		RetryService.Trace t = new RetryService.Trace(1, "raw", List.of(), 100, 200, 10L, false);
		
		LlmCallLog row = LlmCallLogService.recordToEntity(t, 5L, 11L, "qwen2.5:7b", NOW);
		
		assertThat(row.isSuccess());
		assertThat(row.getFailureType());
		assertThat(row.getFailCodes());
		assertEquals(10, row.getResponseTimeMs());
		assertEquals(1, row.getAttemptNo());
		assertEquals(5L, row.getEventId());
		assertEquals(11L, row.getVersionId());
		assertEquals(NOW, row.getCreatedAt());
	}
	
	@Test
	@DisplayName("검증 실패 시도는 VALIDATION_FAIL, 코드는 파이프로 붙는다")
	void 검증_실패_VALIDATION_FAIL_기록() {
		RetryService.Trace t = new RetryService.Trace(1, "raw",
				List.of(new Failure("lost_benefits", "benefits 영역 없음"),
						new Failure("few_benefits", "benefits 항목이 1개 입니다. 2개 이상 필요")),
				100, 200, 10L, false);
		
		LlmCallLog row = LlmCallLogService.recordToEntity(t, 5L, null, "qwen2.5:7b", NOW);
		
		assertFalse(row.isSuccess());
		assertEquals(LlmCallLog.FailureType.VALIDATION_FAIL, row.getFailureType());
		assertEquals("lost_benefits|few_benefits", row.getFailCodes());
		assertNull(row.getVersionId());
	}
	
	@Test
	@DisplayName("잘린 시도는 TRUNCATED - develop RetryService 실물 구조 그대로")
	void 잘림_시도_TRUNCATED_기록() {
		// RetryService.run() 은 잘림을 fails 맨 앞(0 번)에 "truncated" 코드로 넣고
		// Trace.truncated=true 로 남긴다. 실물과 같은 구조로 재현한다.
		RetryService.Trace t = new RetryService.Trace(1, "raw",
				List.of(new Failure("truncated", "출력이 너무 길어 중간에 잘림. 항목 수와 문장을 줄여 더 짧게 만드세요."),
						new Failure("lost_benefits", "benefits 영역이 없음")),
				100, 200, 10L, true);
		
		LlmCallLog row = LlmCallLogService.recordToEntity(t, 5L, null, "qwen2.5:7b", NOW);
		
		assertFalse(row.isSuccess());
		assertEquals(LlmCallLog.FailureType.TRUNCATED, row.getFailureType());
		assertEquals("truncated|lost_benefits", row.getFailCodes(),
				"잘림이 코드 목록을 덮어쓰면 RAG facet 재료가 사라짐");
	}
	
	@Test
	@DisplayName("truncated 플래그가 없어도 코드 'truncated' 가 있으면 TRUNCATED")
	void 잘림_코드만_있어도_TRUNCATED() {
		// 방어 변형 - 기록 경로가 바뀌어 플래그가 유실되어도 코드 기준으로 판정
		RetryService.Trace t = new RetryService.Trace(1, "raw",
				List.of(new Failure("lost_benefits", "benefits 영역이 없습니다."),
						new Failure("truncated", "출력이 너무 길어 중간에 잘림. 항목 수와 문장을 줄여 더 짧게 만드세요.")),
				100, 200, 10L, false);
		
		LlmCallLog row = LlmCallLogService.recordToEntity(t, 5L, null, "qwen2.5:7b", NOW);
		
		assertEquals(LlmCallLog.FailureType.TRUNCATED, row.getFailureType());
		assertEquals(2, row.getFailCodes().split(Pattern.quote("|")).length);
	}
	
	@Test
	@DisplayName("전체 결과는 시도 수 만큼 행으로 남고, versionId 는 마지막 성공에만 붙는다.")
	void 전체_시도_행으로_기록() {
		when(logs.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
		RetryService.Trace fail = new RetryService.Trace(1, "raw", 
				List.of(new Failure("lost_benefits", "benefits 영역이 없음")), 100, 200, 10L, false);
		RetryService.Trace pass = new RetryService.Trace(2, "raw", List.of(), 100, 200, 10L, false);
		RetryService.Result r = new RetryService.Result(true, "<section>", List.of(fail, pass));
		
		List<LlmCallLog> rows = newService().record(r, 5L, 11L, "qwen2.5:7b");
		
		assertEquals(2, rows.size());
		assertFalse(rows.get(0).isSuccess());
		assertEquals(1, rows.get(0).getAttemptNo());
		assertNull(rows.get(0).getVersionId(),
				"선행 실패 시도에 versionId 가 붙으면 '저장까지 이어진 호출만' 정책이 깨집니다.");
		assertTrue(rows.get(1).isSuccess());
		assertEquals(2, rows.get(1).getAttemptNo());
		assertEquals(11L, rows.get(1).getVersionId());
		assertNull(rows.get(1).getFailureType());
	}
	
	@Test
	@DisplayName("실패로 끝난 결과는 어떤 행에도 versionId 가 붙지 않는다.")
	void 실패_결과_versionId_없음() {
		when(logs.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
		RetryService.Trace fail1 = new RetryService.Trace(1, "raw",
				List.of(new Failure("lost_benefits", "benefits 영역이 없음.")), 100, 200, 10L, false);
		RetryService.Trace fail2 = new RetryService.Trace(2, "raw",
				List.of(new Failure("lost_benefits", "benefits 영역이 없음.")), 100, 200, 10L, false);
		RetryService.Result r = new RetryService.Result(false, null, List.of(fail1, fail2));
		
		List<LlmCallLog> rows = newService().record(r, 5L, 11L, "qwen2.5:7b");
		
		assertEquals(2, rows.size());
		assertTrue(rows.stream().allMatch(row -> row.getVersionId() == null),
				"실패 결과에 versionId 가 붙으면 저장 전 호출인데 버전이 연결된 것처럼 보임");
	}
	
	@Test
	@DisplayName("호출 자체 실패는 한 행, 토큰 0 - 응답 시간 null 로 남는다.")
	void 호출_실패_한_행으로_기록() {
		when(logs.save(any(LlmCallLog.class))).thenAnswer(inv -> inv.getArgument(0));
		
		LlmCallLog row = newService().recordCallFailure(5L, null, "qwen2.5:7b", LlmCallLog.FailureType.LLM_ERROR);
		
		assertFalse(row.isSuccess());
		assertEquals(LlmCallLog.FailureType.LLM_ERROR, row.getFailureType());
		assertNull(row.getResponseTimeMs());
		assertNull(row.getFailCodes());
		assertEquals(0, row.getInputTokens());
	}
	
	// ---------------- 일일 상한 ------------------
	@Test
	@DisplayName("사용량이 상한 직전이면 통과, 상한이면 거부한다.")
	void 상한_경계에서_거부() {
		when(logs.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
		.thenReturn((long) LIMIT - 1)
		.thenReturn((long) LIMIT);
		
		assertDoesNotThrow(() -> newService().checkDailyLimit());
		
		LlmDailyLimitExceededException ex = assertThrows(LlmDailyLimitExceededException.class,
				() -> newService().checkDailyLimit());
		assertEquals(LIMIT, ex.getDailyLimit());
		assertEquals(LIMIT, ex.getUsed());
	}
	
	@Test
	@DisplayName("오늘 범위는 KST 자정으로 계산 - 레포에 정확한 경계를 준다.")
	void 오늘_범위_KST_자정으로_계산(){
		when(logs.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
		
		newService().checkDailyLimit();
		
		ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
		verify(logs).countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start.capture(), end.capture());
		
		assertEquals(Instant.parse("2026-09-20T15:00:00Z"), start.getValue(),
				"= 2026-09-21 00:00 KST. UTC 자정을 넘기면 일일 상한이 어긋남");
		assertEquals(Instant.parse("2026-09-21T15:00:00Z"), end.getValue(),
				"= 2026-09-22 00:00 KST. (start, end) 라 자정 0시는 '오늘'이 아님.");
	}
}
