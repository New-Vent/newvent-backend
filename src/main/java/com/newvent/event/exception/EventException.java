package com.newvent.event.exception;

import com.newvent.common.exception.BaseException;
import com.newvent.event.exception.code.EventErrorCode;

public abstract class EventException extends BaseException {

    protected EventException(EventErrorCode errorCode) {
        super(errorCode);
    }
}
