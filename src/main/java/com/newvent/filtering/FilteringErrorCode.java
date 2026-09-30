package com.newvent.filtering;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FilteringErrorCode implements ErrorCode {
    INVALID_PRIVACY_CONFIRMATION(HttpStatus.CONFLICT, "FILTER409-2", "확인한 요청과 내용이 다릅니다. 개인정보 포함 여부를 다시 확인해 주세요."),
    INVALID_CLARIFICATION(HttpStatus.CONFLICT, "FILTER409-0", "이어갈 확인 요청이 없거나 만료되었습니다. 수정 요청을 다시 입력해 주세요."),
    STALE_CLARIFICATION(HttpStatus.CONFLICT, "FILTER409-1", "확인 요청 이후 작업 버전이 변경되었습니다. 현재 페이지를 확인하고 다시 요청해 주세요."),
    CONTEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "FILTER400-3", "이어진 요청이 너무 깁니다. 변경할 내용을 하나의 요청으로 정리해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
