package com.newvent.common.error;

import lombok.Getter;

/**
 * 일일 상한 초과 신호용 예외
 */
@Getter
public class LlmDailyLimitExceededException extends RuntimeException {

	private final int dailyLimit;
	private final long used;
	public LlmDailyLimitExceededException(int dailyLimit, long used) {
		super("LLM 일일 호출 상한 초과 : " + used + "/" + dailyLimit);
		this.dailyLimit = dailyLimit;
		this.used = used;
	}
}
