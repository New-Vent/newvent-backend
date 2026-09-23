package com.newvent.common.exception.handler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.newvent.common.exception.BaseException;
import com.newvent.common.exception.code.CommonErrorCode;
import com.newvent.common.exception.code.ErrorCode;
import com.newvent.common.response.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 도메인에서 의도적으로 발생시킨 예외를 처리
    // 예외가 가진 HTTP 상태, 에러 코드, 메시지를 그대로 응답
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

    // @RequestBody에 적용된 @Valid 검증 실패를 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e
    ) {
        ObjectError error = e.getBindingResult().getFieldError();

        if (error == null) {
            error = e.getBindingResult().getGlobalError();
        }

        String message = error != null && error.getDefaultMessage() != null
                ? error.getDefaultMessage()
                : CommonErrorCode.INVALID_INPUT_VALUE.getMessage();

        return createInvalidInputResponse(message);
    }

    // @Validated를 통한 요청 파라미터 및 경로 변수의 제약조건 위반을 처리
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException e
    ) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(violationMessage ->
                        violationMessage != null && !violationMessage.isBlank()
                )
                .findFirst()
                .orElse(CommonErrorCode.INVALID_INPUT_VALUE.getMessage());

        return createInvalidInputResponse(message);
    }

    // Spring MVC의 메서드 파라미터 검증 실패를 처리
    // @RequestParam, @PathVariable 등에 직접 선언한 제약조건이 대상
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException e
    ) {
        String message = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(errorMessage -> errorMessage != null && !errorMessage.isBlank())
                .findFirst()
                .orElse(CommonErrorCode.INVALID_INPUT_VALUE.getMessage());

        return createInvalidInputResponse(message);
    }

    // JSON 문법 오류, 필드 타입 불일치 등 요청 본문을 읽을 수 없는 경우를 처리
    // 내부 역직렬화 오류는 노출하지 않고 공통 메시지를 반환
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException() {
        return createInvalidInputResponse(
                CommonErrorCode.INVALID_INPUT_VALUE.getMessage()
        );
    }

    // 별도로 처리되지 않은 예상하지 못한 예외를 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("exception : ", e);

        return ResponseEntity
                .status(CommonErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ErrorResponse.of(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }

    private ResponseEntity<ErrorResponse> createInvalidInputResponse(String message) {
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
                .body(ErrorResponse.of(
                        CommonErrorCode.INVALID_INPUT_VALUE.getCode(),
                        message
                ));
    }
}
