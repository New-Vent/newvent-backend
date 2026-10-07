package com.newvent.editor.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 수정 진입부에서 거부하는 사유
 *
 * ★ GenerationErrorCode 를 그대로 쓰지 않는 것만 여기 둔다
 */
@Getter
@RequiredArgsConstructor
public enum EditErrorCode implements ErrorCode {

    /**
     * ★ 404 다. 고칠 대상이 없다.
     */
    PAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "EDIT404-0",
            "아직 만들어진 페이지가 없습니다. 먼저 페이지를 만들어 주세요."),

    /**
     * ★ 409 다. 요청은 멀쩡한데 지금은 안 될 뿐이다.
     */
    ALREADY_RUNNING(HttpStatus.CONFLICT, "EDIT409-0",
            "이 이벤트에 진행 중인 작업이 있습니다. 끝난 뒤 다시 시도해 주세요."),

    /**
     * ★ 400 다. 고른 영역이 레지스트리에 없거나 채팅으로 못 고치는 영역(유의사항)이다.
     *   프론트가 고를 수 있는 영역만 보내야 한다 — 여기 오면 프론트 버그다.
     */
    INVALID_BLOCK(HttpStatus.BAD_REQUEST, "EDIT400-0",
            "고를 수 없는 영역입니다. 유의사항은 채팅으로 고칠 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
