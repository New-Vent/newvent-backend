package com.newvent.event.exception;

public class EventNotAccessibleException extends EventException {

    public EventNotAccessibleException() {
        super(EventErrorCode.EVENT_NOT_ACCESSIBLE);
    }
}
