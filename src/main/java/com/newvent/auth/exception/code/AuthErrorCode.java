package com.newvent.auth.exception.code;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 토큰이 없거나 무효한 요청(필터 체인의 401), 역할 부족(403)은 여기가 아니라
 * CommonErrorCode.UNAUTHORIZED / ACCESS_DENIED 를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    // 없는 계정·틀린 비밀번호·비활성 관리자를 구분하지 않는다 (계정 존재 여부 노출 방지)
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH401-0", "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401-1", "유효하지 않은 Refresh Token 입니다."),
    CSRF_ORIGIN_MISMATCH(HttpStatus.FORBIDDEN, "AUTH403-0", "요청 출처를 확인할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
