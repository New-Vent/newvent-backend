package com.newvent.auth.exception;

import com.newvent.auth.exception.code.AuthErrorCode;

public class InvalidCredentialsException extends AuthException {

    public InvalidCredentialsException() {
        super(AuthErrorCode.INVALID_CREDENTIALS);
    }
}
