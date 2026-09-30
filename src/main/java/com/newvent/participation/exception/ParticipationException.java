package com.newvent.participation.exception;

import com.newvent.common.exception.BaseException;

public class ParticipationException extends BaseException {

    public ParticipationException(ParticipationErrorCode errorCode) {
        super(errorCode);
    }
}
