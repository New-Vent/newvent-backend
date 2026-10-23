package com.newvent.rag.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;

public enum RagErrorCode implements ErrorCode {
	// NEWVENT-BACKEND-6·7. 색인은 지우기→임베딩→다시 넣기 순서라 같은 버전에 실행이 겹치면
	// 유니크 제약 위반·삭제 줄 수 검증 실패가 번갈아 난다. 서비스가 자리 선점 실패로 던진다.
	REINDEX_IN_PROGRESS(HttpStatus.CONFLICT, "RAG409-0", "같은 버전의 재색인이 이미 진행 중입니다. 잠시 뒤 다시 시도해 주세요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	RagErrorCode(HttpStatus httpStatus, String code, String message) {
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
