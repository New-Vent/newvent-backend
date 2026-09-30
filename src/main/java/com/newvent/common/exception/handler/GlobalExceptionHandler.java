package com.newvent.common.exception.handler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.newvent.common.exception.BaseException;
import com.newvent.common.exception.code.CommonErrorCode;
import com.newvent.common.exception.code.ErrorCode;
import com.newvent.common.response.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 파라미터·경로 변수의 타입 변환 실패
    // 예: ?status=FOO, /events/abc, ?page=abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e
    ) {
        return createInvalidInputResponse("'" + e.getName() + "' 값의 형식이 올바르지 않습니다.");
    }

    // 필수 요청 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e
    ) {
        return createInvalidInputResponse("'" + e.getParameterName() + "' 파라미터는 필수입니다.");
    }

    // 존재하지 않는 경로
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException() {
        return createErrorResponse(CommonErrorCode.NOT_FOUND);
    }

    // 지원하지 않는 HTTP 메서드
    // Spring이 제공하는 Allow 헤더도 함께 반환
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e
    ) {
        CommonErrorCode errorCode = CommonErrorCode.METHOD_NOT_ALLOWED;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .headers(e.getHeaders())
                .body(ErrorResponse.of(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

    // 지원하지 않는 Content-Type
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e
    ) {
        CommonErrorCode errorCode = CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .headers(e.getHeaders())
                .body(ErrorResponse.of(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

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

    // 인증되지 않은 요청(토큰 없음·서명/만료 검증 실패)을 처리
    // 필터 체인에서 난 예외라 SecurityConfig 의 인증 진입점이 HandlerExceptionResolver 로 넘겨 여기에 도달한다
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException() {
        return createErrorResponse(CommonErrorCode.UNAUTHORIZED);
    }

    // 인증은 됐지만 권한이 부족한 요청을 처리 (필터 체인의 인가 거부 + 컨트롤러의 @PreAuthorize)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException() {
        return createErrorResponse(CommonErrorCode.ACCESS_DENIED);
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

    private ResponseEntity<ErrorResponse> createErrorResponse(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(
                        errorCode.getCode(),
                        errorCode.getMessage()
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
