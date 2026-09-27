package com.newvent.event.exception;

import com.newvent.event.exception.code.EventErrorCode;

public class EventNotAccessibleException extends EventException {

    public EventNotAccessibleException() {
        super(EventErrorCode.EVENT_NOT_ACCESSIBLE);
    }
}
