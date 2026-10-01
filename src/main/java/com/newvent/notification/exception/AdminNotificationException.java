package com.newvent.notification.exception;

import com.newvent.common.exception.BaseException;

public class AdminNotificationException extends BaseException {

    public AdminNotificationException(AdminNotificationErrorCode errorCode) {
        super(errorCode);
    }
}
