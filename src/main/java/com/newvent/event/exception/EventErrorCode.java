package com.newvent.event.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.error.ErrorCode;

public enum EventErrorCode implements ErrorCode {
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "EVENT400-0", "종료일시는 시작일시보다 이후여야 합니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT404-0", "이벤트를 찾을 수 없습니다."),
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT404-1", "템플릿을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    EventErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
