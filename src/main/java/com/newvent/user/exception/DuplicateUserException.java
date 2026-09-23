package com.newvent.user.exception;

public class DuplicateUserException extends UserException {

    public DuplicateUserException(UserErrorCode errorCode) {
        super(errorCode);
    }
}
