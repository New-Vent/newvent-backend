package com.newvent.event.exception.code;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventErrorCode implements ErrorCode {

    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT404-0", "존재하지 않는 이벤트입니다."),
    EVENT_NOT_ACCESSIBLE(HttpStatus.NOT_FOUND, "EVENT404-1", "지금은 접근할 수 없는 이벤트입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
