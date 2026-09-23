package com.newvent.generation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.event.domain.Event;

// LlmCallLog 생성 불변식을 잠근다. V1 CHECK 4종을 앱 생성 시점에 미러링 -
// 매핑(recordToEntity)이 이 규칙을 지킬지 보장해주는 것.
public class LlmCallLogTest {
	
	private static final Instant AT = Instant.parse("2026-09-21T03:00:00Z");
	
	// 15인자 중 고정분을 숨긴 헬퍼 - 검증 대상 5개만 드러낸다
	private static LlmCallLog 행(boolean truncated, boolean callOk, boolean validOk,
			FailureType type, String message) {
		return LlmCallLog.create(mock(Event.class), null, UUID.randomUUID(), 1,
				"qwen2.5:7b", "mock", 100, 200, 10, truncated, callOk, validOk, type, message, AT);
	}
	
	@Test
	@DisplayName("valid 인데 실패 정보(failureType/failureMessage)가 있으면 거부한다.")
	void 성공_실패정보_못섞음() {
		assertThrows(IllegalArgumentException.class, () ->
			행(false, true, true, FailureType.VALIDATION_FAIL, null));
		assertThrows(IllegalArgumentException.class, () ->
			행(false, true, true, null, "benefits 영역이 없습니다."));
	}
	
	@Test
	@DisplayName("invalid 인데 분류(failureType)가 없으면 거부한다.")
	void 실패_분류없으면_거부() {
		assertThrows(IllegalArgumentException.class, () ->
			행(false, true, false, null, null));
	}
	
	@Test
	@DisplayName("valid 인데 callOk=false 면 거부한다 (ck_valid_requires_call).")
	void valid인데_call실패_거부() {
		assertThrows(IllegalArgumentException.class, () ->
			행(false, false, true, null, null));
	}
	
	@Test
	@DisplayName("truncated=true 인데 callOk·validOk·TRUNCATED 조합이 아니면 거부한다.")
	void 잘림_상태불일치_거부() {
		assertThrows(IllegalArgumentException.class, () ->
			행(true, false, false, FailureType.TRUNCATED, null));
		assertThrows(IllegalArgumentException.class, () ->
			행(true, true, false, FailureType.VALIDATION_FAIL, null));
	}
	
	@Test
	@DisplayName("failureType 이 TRUNCATED 인데 truncated=false 면 거부한다.")
	void 분류만_TRUNCATED_거부() {
		assertThrows(IllegalArgumentException.class, () ->
			행(false, true, false, FailureType.TRUNCATED, null));
	}
	
	@Test
	@DisplayName("성공 행은 callOk·validOk true, 실패 정보 null 로 유지된다.")
	void 성공_행_기본값() {
		UUID req = UUID.randomUUID();
		Event event = mock(Event.class);
		LlmCallLog row = LlmCallLog.create(event, null, req, 1,
				"qwen2.5:7b", "mock", 100, 200, 10, false, true, true, null, null, AT);
		
		assertTrue(row.isCallOk());
		assertTrue(row.isValidOk());
		assertFalse(row.isTruncated());
		assertNull(row.getFailureType());
		assertNull(row.getFailureMessage());
		assertEquals("mock", row.getProvider());
		assertEquals(event, row.getEvent());
		assertEquals(req, row.getRequestId());
		assertEquals(AT, row.getCreatedAt());
	}
}