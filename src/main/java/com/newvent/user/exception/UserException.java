package com.newvent.user.exception;

import com.newvent.common.exception.BaseException;

public abstract class UserException extends BaseException {

    protected UserException(UserErrorCode errorCode) {
        super(errorCode);
    }
}
