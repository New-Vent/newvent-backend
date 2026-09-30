package com.newvent.event.exception;

import com.newvent.common.exception.BaseException;

public class DirectEditException extends BaseException {

    public DirectEditException(DirectEditErrorCode errorCode) {
        super(errorCode);
    }
}
