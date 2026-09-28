package com.newvent.auth.exception;

import com.newvent.auth.exception.code.AuthErrorCode;

/** /auth/refresh, /auth/logout 요청의 Origin(Referer) 이 허용 목록에 없다. */
public class CsrfOriginException extends AuthException {

    public CsrfOriginException() {
        super(AuthErrorCode.CSRF_ORIGIN_MISMATCH);
    }
}
