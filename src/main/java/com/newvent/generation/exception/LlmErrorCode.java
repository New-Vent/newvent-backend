package com.newvent.generation.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** LLM 도메인 에러 코드 */
@Getter
@RequiredArgsConstructor
public enum LlmErrorCode implements ErrorCode {

    DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "LLM429-0", "일일 호출 상한을 초과했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
