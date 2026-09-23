package com.newvent.event.exception;

import com.newvent.common.exception.BaseException;

public class EventException extends BaseException {
    public EventException(EventErrorCode errorCode) {
        super(errorCode);
    }
}
