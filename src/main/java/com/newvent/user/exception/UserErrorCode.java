package com.newvent.user.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "USER409-0", "이미 사용 중인 로그인 ID입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER409-1", "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404-0", "존재하지 않는 사용자입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
