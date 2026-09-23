package com.newvent.user.exception;

import com.newvent.common.exception.BaseException;
import com.newvent.user.exception.code.UserErrorCode;

public abstract class UserException extends BaseException {

    protected UserException(UserErrorCode errorCode) {
        super(errorCode);
    }
}
