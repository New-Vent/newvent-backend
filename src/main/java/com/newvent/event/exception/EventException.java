package com.newvent.event.exception;

import com.newvent.common.error.BaseException;

public class EventException extends BaseException {
    public EventException(EventErrorCode errorCode) {
        super(errorCode);
    }
}
