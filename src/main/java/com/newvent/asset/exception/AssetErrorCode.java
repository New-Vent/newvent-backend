package com.newvent.asset.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

public enum AssetErrorCode implements ErrorCode {
    NOT_AN_IMAGE(HttpStatus.BAD_REQUEST, "ASSET400-0", "이미지 파일이 아닙니다. PNG · JPG · WebP · GIF 만 올릴 수 있습니다."),
    TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "ASSET413-0", "이미지가 너무 큽니다."),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "ASSET400-1", "빈 파일입니다."),
    TOO_MANY(HttpStatus.BAD_REQUEST, "ASSET400-2", "한 이벤트에 올릴 수 있는 이미지 수를 넘었습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "ASSET404-0", "이미지를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    AssetErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
