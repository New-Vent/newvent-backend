package com.newvent.generation.exception;

import com.newvent.common.exception.BaseException;

import lombok.Getter;

/**
 * 일일 상한 초과 신호용 예외
 */
@Getter
public class LlmDailyLimitExceededException extends BaseException {

    private final int dailyLimit;
    private final long used;

    public LlmDailyLimitExceededException(int dailyLimit, long used) {
        super(LlmErrorCode.DAILY_LIMIT_EXCEEDED);
        this.dailyLimit = dailyLimit;
        this.used = used;
    }
}
