package com.newvent.user.exception;

import com.newvent.user.exception.code.UserErrorCode;

public class DuplicateUserException extends UserException {

    public DuplicateUserException(UserErrorCode errorCode) {
        super(errorCode);
    }
}
