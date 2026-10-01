package com.newvent.generation.exception;

import com.newvent.common.exception.BaseException;

import lombok.Getter;

/** 로그 상세 조회 실패 신호용 예외 */
@Getter
public class LlmCallLogNotFoundException extends BaseException {

    private final long logId;

    public LlmCallLogNotFoundException(long logId) {
        super(LlmErrorCode.LOG_NOT_FOUND);
        this.logId = logId;
    }
}
