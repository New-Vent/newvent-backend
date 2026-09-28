package com.newvent.event.exception;

public class EventNotFoundException extends EventException {

    public EventNotFoundException() {
        super(EventErrorCode.EVENT_NOT_FOUND);
    }
}
