package com.newvent.generation.exception;

import com.newvent.common.exception.BaseException;
import com.newvent.common.exception.code.ErrorCode;

/**
 * 생성 도메인에서 **의도적으로** 던지는 예외. API 규약 §5.1
 *
 * ★ GlobalExceptionHandler 가 ErrorCode 의 상태·코드·메시지를 그대로 응답으로 만든다.
 *   그래서 컨트롤러에 try-catch 가 없다.
 *
 * ★ 이건 "거부" 이지 "고장" 이 아니다.
 *   모델 호출 실패 같은 진짜 고장은 워커 스레드 안에서 잡아서
 *   GenerationJob.fail() 로 옮긴다 — HTTP 응답이 이미 202 로 나간 뒤이기 때문이다.
 */
public class GenerationException extends BaseException {

    public GenerationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
