package com.newvent.user.exception;

import com.newvent.user.exception.code.UserErrorCode;

public class UserNotFoundException extends UserException {

    public UserNotFoundException() {
        super(UserErrorCode.USER_NOT_FOUND);
    }
}
