package com.newvent.event.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.BaseException;
import com.newvent.common.exception.code.ErrorCode;

public class DirectEditException extends BaseException {

    public DirectEditException(DirectEditErrorCode errorCode) {
        super(errorCode);
    }

    public DirectEditException(DirectEditErrorCode errorCode, String reason) {
        super(new ErrorCode() {
            @Override public HttpStatus getHttpStatus() { return errorCode.getHttpStatus(); }
            @Override public String getCode()           { return errorCode.getCode(); }
            @Override public String getMessage()        { return reason; }
        });
    }
}
