package com.newvent.user.exception;

public class UserNotFoundException extends UserException {

    public UserNotFoundException() {
        super(UserErrorCode.USER_NOT_FOUND);
    }
}
