# API 규약

이 문서는 New-Vent 서비스의 관리자 프론트엔드, 사용자 프론트엔드, 백엔드가 공통으로 사용하는 API 계약을 정의한다.

## 1. DTO 작성 규칙

- Request 및 Response DTO는 Java `record`로 작성한다.
- Entity를 API 응답으로 직접 반환하지 않는다.
- Entity는 Response DTO의 정적 팩토리 메서드 `from()`을 통해 변환한다.
- 성공 응답은 공통 `ApiResponse<T>`로 감싸서 반환한다.

```java
public record EventResponse(
        Long id,
        String title
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle()
        );
    }
}
```

## 2. 성공 응답

### 2.1 응답 구조

성공 응답은 `ApiResponse<T>` 형식으로 반환한다.

```java
public record ApiResponse<T>(
        boolean success,
        T data,
        String message
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public static <T> ApiResponse<T> successNoData() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> successNoData(String message) {
        return new ApiResponse<>(true, null, message);
    }
}
```

### 2.2 응답 예시

데이터가 있는 응답:

```json
{
  "success": true,
  "data": {
    "id": 1,
    "title": "가을 프로모션"
  },
  "message": null
}
```

데이터가 없는 응답:

```json
{
  "success": true,
  "data": null,
  "message": "요청이 정상적으로 처리되었습니다."
}
```

### 2.3 HTTP 204 응답

`204 No Content`를 반환하는 API는 응답 본문을 포함하지 않는다. 따라서 `ApiResponse<T>`로 감싸지 않는다.

## 3. 실패 응답

### 3.1 응답 구조

실패 응답은 공통 `ErrorResponse` 형식으로 반환한다.

```java
public record ErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(
                code,
                message,
                LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        );
    }
}
```

### 3.2 응답 예시

```json
{
  "code": "EVENT404-0",
  "message": "이벤트를 찾을 수 없습니다.",
  "timestamp": "2026-09-23T00:54:10"
}
```

정상 응답과 실패 응답은 서로 다른 형식을 사용한다. 클라이언트는 HTTP 상태 코드를 기준으로 두 응답을 구분한다.

## 4. 에러 코드

### 4.1 ErrorCode 인터페이스

모든 도메인 에러 코드 enum은 공통 `ErrorCode` 인터페이스를 구현한다.

```java
public interface ErrorCode {
    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();
}
```

### 4.2 에러 코드 명명 규칙

에러 코드는 다음 형식을 사용한다.

```text
{도메인명}{HTTP 상태 코드}-{일련번호}
```

| 구성 | 설명 | 예시 |
| --- | --- | --- |
| 도메인명 | 에러가 발생한 도메인을 영문 대문자로 작성 | `COMMON`, `USER`, `EVENT` |
| HTTP 상태 코드 | 해당 에러의 HTTP 상태 코드 | `400`, `401`, `404`, `500` |
| 일련번호 | 같은 도메인과 HTTP 상태 코드 안에서 0부터 순차적으로 부여 | `0`, `1`, `2` |

예시:

- `USER404-0`
- `USER404-1`
- `EVENT404-0`
- `COMMON400-0`
- `COMMON500-0`

### 4.3 공통 에러 코드

| 에러 코드 | HTTP 상태 | 메시지 |
| --- | --- | --- |
| `COMMON400-0` | 400 Bad Request | 잘못된 입력값입니다. |
| `COMMON401-0` | 401 Unauthorized | 인증이 필요합니다. |
| `COMMON403-0` | 403 Forbidden | 접근 권한이 없습니다. |
| `COMMON500-0` | 500 Internal Server Error | 서버 내부 오류가 발생했습니다. |

도메인별 에러 코드는 각 도메인의 `~ErrorCode` enum에서 관리한다.

## 5. 예외 처리

### 5.1 예외 계층

도메인별 예외는 공통 `BaseException`을 상속한다.

```text
RuntimeException
└── BaseException
    ├── EventException
    ├── UserException
    └── ...
```

```java
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

### 5.2 전역 예외 처리

`GlobalExceptionHandler`는 `@RestControllerAdvice`를 사용하여 애플리케이션의 예외를 공통 형식으로 변환한다.

| 예외 | 처리 방식 |
| --- | --- |
| `BaseException` | 해당 `ErrorCode`의 HTTP 상태, 코드, 메시지를 반환 |
| `MethodArgumentNotValidException` | `COMMON400-0`을 반환하며 첫 번째 필드 검증 메시지를 우선 사용 |
| `Exception` | 내부 정보를 노출하지 않고 `COMMON500-0`을 반환하며 상세 예외를 서버 로그에 기록 |

## 6. Validation

- DTO에 Jakarta Validation 어노테이션을 적용한다.
- Validation은 class가 아닌 `record` DTO에도 동일하게 적용한다.
- 필요한 경우 커스텀 Validation을 작성한다.
- Validation 실패는 `GlobalExceptionHandler`에서 `COMMON400-0` 응답으로 변환한다.

```java
public record EventCreateRequest(
        @NotBlank(message = "이벤트 제목은 필수입니다.")
        @Size(max = 100, message = "이벤트 제목은 100자 이하여야 합니다.")
        String title
) {
}
```

## 7. 클라이언트 처리 기준

- HTTP 2xx 응답은 성공 응답으로 처리한다.
- HTTP 4xx 응답은 요청값, 인증, 권한 또는 비즈니스 규칙 오류로 처리한다.
- HTTP 5xx 응답은 서버 내부 오류로 처리한다.
- 화면에 표시할 메시지는 `message` 필드를 사용한다.
- 서버 내부 예외 상세 내용이나 스택 트레이스는 클라이언트에 노출하지 않는다.

