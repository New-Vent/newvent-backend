package com.newvent.auth.exception;

import com.newvent.auth.exception.code.AuthErrorCode;

/** Refresh Token 이 없거나, 만료·폐기·재사용됐거나, 주인 계정이 더 이상 로그인할 수 없다. */
public class InvalidRefreshTokenException extends AuthException {

    public InvalidRefreshTokenException() {
        super(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
}
