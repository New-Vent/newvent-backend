package com.newvent.generation.domain;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// LlmCallLog 생성 불변식을 잠근다. 매핑(recordToEntity)이 이 규칙을 지킬 지 보장해주는 것
public class LlmCallLogTest {
	
	private static final Instant AT = Instant.parse("2026-09-21T03:00:00Z");
	
	@Test
	@DisplayName("성공 호출에 실패 정보 (failureType/failCodes)를 섞으면 거부한다.")
	void 성공_실패정보_못섞음() {
		assertThrows(IllegalArgumentException.class, () -> 
			LlmCallLog.create(1L, null, 1, "qwen2.5:7b", 100, 200, 10, true,
				LlmCallLog.FailureType.VALIDATION_FAIL, null, AT));
		assertThrows(IllegalArgumentException.class, () ->
			LlmCallLog.create(1L, null, 1, "qwen2.5:7b", 100, 200, 10, true,
				null, "lost_benefits", AT));
	}
	
	@Test
	@DisplayName("실패 호출은 분류(failureType)가 없으면 거부한다.")
	void 실패_분류없으면_거부() {
		assertThrows(IllegalArgumentException.class, () -> 
			LlmCallLog.create(1L, null, 1, "qwen2.5:7b", 100, 200, 10, false,
					null, "lost_benefits", AT));
		assertThrows(IllegalArgumentException.class, () ->
			LlmCallLog.create(1L, null, 1, "qwen2.5:7b", 100, 200, 10, false,
					null, null, AT));
	}
	
	@Test
	@DisplayName("성공 행은 실패 정보가 null로 유지된다.")
	void 성공_행_기본값() {
		LlmCallLog row = LlmCallLog.create(1L, 9L, 1, "qwen2.5:7b", 100, 200, 10, true,
				null, null, AT);
		
		assertTrue(row.isSuccess());
		assertNull(row.getFailureType());
		assertNull(row.getFailCodes());
	}
}
