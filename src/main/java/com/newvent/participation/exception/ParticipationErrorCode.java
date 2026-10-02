package com.newvent.participation.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

public enum ParticipationErrorCode implements ErrorCode {
    INVALID_SUBMITTED_DATA(HttpStatus.BAD_REQUEST, "PARTICIPATION400-0", "참여 입력값이 없거나 올바르지 않습니다."),
    INSUFFICIENT_GRADE(HttpStatus.FORBIDDEN, "PARTICIPATION403-0", "이벤트 참여에 필요한 멤버십 등급이 아닙니다."),
    ALREADY_PARTICIPATED(HttpStatus.CONFLICT, "PARTICIPATION409-0", "이미 참여한 이벤트입니다."),
    PARTICIPATION_NOT_CONFIGURED(HttpStatus.CONFLICT, "PARTICIPATION409-1", "이벤트의 참여 설정이 없거나 올바르지 않습니다."),
    UNSUPPORTED_PARTICIPATION_TYPE(HttpStatus.CONFLICT, "PARTICIPATION409-2", "지원하지 않거나 비활성화된 참여 방식입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ParticipationErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() { return httpStatus; }

    @Override
    public String getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
