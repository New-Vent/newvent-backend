package com.newvent.rag.exception;

import com.newvent.common.exception.BaseException;

public class RagException extends BaseException {

	public RagException(RagErrorCode errorCode) {
		super(errorCode);
	}
}
