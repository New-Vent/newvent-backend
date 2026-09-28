package com.newvent.auth.exception;

import com.newvent.auth.exception.code.AuthErrorCode;
import com.newvent.common.exception.BaseException;

public abstract class AuthException extends BaseException {

    protected AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }
}
