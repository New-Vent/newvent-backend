package com.newvent.editor.exception;

import com.newvent.common.exception.BaseException;
import com.newvent.common.exception.code.ErrorCode;

/**
 * 수정 진입부에서 의도적으로 던지는 예외.
 * */
public class EditException extends BaseException {

    public EditException(ErrorCode errorCode) {
        super(errorCode);
    }
}
