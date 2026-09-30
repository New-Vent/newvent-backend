package com.newvent.participation.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

public enum ParticipationErrorCode implements ErrorCode {
    INSUFFICIENT_GRADE(HttpStatus.FORBIDDEN, "PARTICIPATION403-0", "이벤트 참여에 필요한 멤버십 등급이 아닙니다."),
    ALREADY_PARTICIPATED(HttpStatus.CONFLICT, "PARTICIPATION409-0", "이미 참여한 이벤트입니다.");

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
