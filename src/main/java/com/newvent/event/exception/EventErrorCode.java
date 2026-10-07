package com.newvent.event.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

public enum EventErrorCode implements ErrorCode {
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "EVENT400-0", "종료일시는 시작일시보다 이후여야 합니다."),
    INVALID_SEARCH_PERIOD(HttpStatus.BAD_REQUEST, "EVENT400-1", "조회 시작일은 조회 종료일보다 늦을 수 없습니다."),
    /** 게시(PUBLISHED)는 게시 API 로만 한다. 상태 변경 API 는 종료만 받는다. */
    UNSUPPORTED_STATUS_CHANGE(HttpStatus.BAD_REQUEST, "EVENT400-2", "상태 변경으로는 종료(ENDED)만 할 수 있습니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT404-0", "이벤트를 찾을 수 없습니다."),
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT404-1", "템플릿을 찾을 수 없습니다."),
    EVENT_NOT_ACCESSIBLE(HttpStatus.NOT_FOUND, "EVENT404-2", "지금은 접근할 수 없는 이벤트입니다."),
    VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT404-3", "이벤트 버전을 찾을 수 없습니다."),
    PUBLISHED_VERSION_CHECKPOINT_UNMARK_FORBIDDEN(HttpStatus.CONFLICT, "EVENT409-0", "현재 게시 중인 버전의 저장 지점은 해제할 수 없습니다."),
    /** REQ-EVT-10. status = ENDED 이거나, PUBLISHED 이면서 기존 종료일시가 지난 경우. */
    EVENT_ENDED_NOT_EDITABLE(HttpStatus.CONFLICT, "EVENT409-1", "종료된 이벤트는 수정할 수 없습니다."),
    PUBLISHED_EVENT_DELETE_FORBIDDEN(HttpStatus.CONFLICT, "EVENT409-2", "게시 중인 이벤트는 삭제할 수 없습니다. 먼저 게시를 내리거나 종료해주세요."),
    EVENT_GENERATING_DELETE_FORBIDDEN(HttpStatus.CONFLICT, "EVENT409-3", "생성 작업이 진행 중인 이벤트는 삭제할 수 없습니다. 작업이 끝난 뒤 다시 시도해주세요."),
    /** 템플릿이 공개 목록 카테고리로도 쓰여서, 게시 중에는 변경·해제를 막는다. */
    PUBLISHED_EVENT_TEMPLATE_NOT_EDITABLE(HttpStatus.CONFLICT, "EVENT409-4", "게시 중인 이벤트는 템플릿을 변경하거나 해제할 수 없습니다."),
    EVENT_ENDED_PUBLISH_FORBIDDEN(HttpStatus.CONFLICT, "EVENT409-5", "종료된 이벤트는 게시할 수 없습니다."),
    // REQ-EVT-07. 종료(ENDED)는 되돌릴 수 없다. 게시 내리기(PUBLISHED → DRAFT)는 종료 전까지만 가능하다
    EVENT_NOT_ENDABLE(HttpStatus.CONFLICT, "EVENT409-6", "게시 중인 이벤트만 종료할 수 있습니다."),
    EVENT_NOT_UNPUBLISHABLE(HttpStatus.CONFLICT, "EVENT409-7", "게시 중인 이벤트만 게시를 내릴 수 있습니다."),
    BUILTIN_TEMPLATE_IMMUTABLE(HttpStatus.FORBIDDEN, "EVENT403-0", "기본 제공 템플릿은 변경할 수 없습니다."),
    INVALID_TEMPLATE_STRUCTURE(HttpStatus.BAD_REQUEST, "EVENT400-3", "템플릿의 필수 블록이나 슬롯 구조를 확인해주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    EventErrorCode(HttpStatus httpStatus, String code, String message) {
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
