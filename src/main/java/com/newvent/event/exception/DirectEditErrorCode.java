package com.newvent.event.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

public enum DirectEditErrorCode implements ErrorCode {
    BEFORE_TEXT_MISMATCH(HttpStatus.CONFLICT, "DIRECT_EDIT409-0", "화면이 최신이 아닙니다. 새로고침 후 다시 시도해주세요."),
    INVALID_TEXT_INDEX(HttpStatus.BAD_REQUEST, "DIRECT_EDIT400-0", "유효하지 않은 텍스트 노드 인덱스입니다."),
    INVALID_BUTTON_STYLE(HttpStatus.BAD_REQUEST, "DIRECT_EDIT400-1", "지원하지 않는 버튼 스타일 값입니다."),
    CTA_NOT_FOUND(HttpStatus.BAD_REQUEST, "DIRECT_EDIT400-2", "참여 버튼([data-slot=\"cta-link\"])을 찾을 수 없습니다."),
    EMPTY_EDIT_REQUEST(HttpStatus.BAD_REQUEST, "DIRECT_EDIT400-3", "수정할 문구나 버튼 스타일, 블록 순서, 배치 · 모양 중 하나는 있어야 합니다."),
    INVALID_BLOCK_ORDER(HttpStatus.BAD_REQUEST, "DIRECT_EDIT400-4", "알 수 없는 블록 이름이 순서 목록에 있습니다."),
    INVALID_LOOK(HttpStatus.BAD_REQUEST, "DIRECT_EDIT400-5", "알 수 없는 배치 · 모양 이름입니다."),
    LOOK_NOT_AVAILABLE(HttpStatus.CONFLICT, "DIRECT_EDIT409-1", "지금 페이지에는 쓸 수 없는 배치 · 모양입니다."),
    INVALID_TEXT_STYLE(HttpStatus.BAD_REQUEST, "DIRECT_EDIT400-6", "지원하지 않는 제목 글자 색 · 크기 값입니다."),
    INVALID_TEXT_FORMAT(HttpStatus.BAD_REQUEST, "DIRECT_EDIT400-7", "문구 색은 HEX 6자리, 크기는 10~96px이어야 합니다."),
    TEXT_DELETE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "DIRECT_EDIT400-8", "필수 영역이나 기능이 포함된 요소는 삭제할 수 없습니다."),
    DUPLICATE_TEXT_INDEX(HttpStatus.BAD_REQUEST, "DIRECT_EDIT400-9", "같은 문구의 변경은 하나로 합쳐 보내주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    DirectEditErrorCode(HttpStatus httpStatus, String code, String message) {
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
