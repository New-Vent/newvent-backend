package com.newvent.editor.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

/**
 * 라우터 거절 사유
 * ★ LlmErrorCode 와 같은 자리. 도메인 예외는 각 도메인 아래 exception에 둠.
 *   (패키지 경계 문서) common 은 ErrorCode 인터페이스만 공용
 *
 * ★ 문서 규약에 {도메인}{상태} - {일련번호} 를 따름.
 *   같은 400이라도 사유가 다르므로 일련번호를 갈라야 프론트가 분기
 */
public enum RouterErrorCode implements ErrorCode {
	// target 이 Block 에 없는 영역
	TARGET_NOT_FOUND(HttpStatus.BAD_REQUEST, "ROUTER400-0", "알 수 없는 영역입니다."),

	// op 가 Op에 없는 값 - 파서가 빈 객체를 통과시킨 경우도 여기에 옴
	UNKNOWN_OP(HttpStatus.BAD_REQUEST, "ROUTER400-1", "무엇을 하실지 알아내지 못했습니다."),

	// 영역 규칙상 허용되지 않는다 — 문구는 Gate 가 준다
    NOT_ALLOWED(HttpStatus.BAD_REQUEST, "ROUTER400-2", "그 영역은 바꿀 수 없습니다.");

	private final HttpStatus status;
    private final String code;
    private final String message;

    RouterErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

	@Override
	public HttpStatus getHttpStatus() {
		return status;
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
