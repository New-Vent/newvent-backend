package com.newvent.generation.exception;

import org.springframework.http.HttpStatus;

import com.newvent.common.exception.code.ErrorCode;
import com.newvent.generation.service.RequestFilter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 생성 도메인 에러 코드. API 규약 §4.2 `{도메인}{상태코드}-{일련번호}`
 *
 * ★ 메시지는 **관리자에게 그대로 보이는 문장**이다 (`REQ-LLM-36`).
 *   내부 오류 원문이 여기 들어오면 안 된다.
 *   "Connection refused: localhost:11434" 는 로그에만 남는다.
 *
 * ★ 글자 수를 상수에서 가져온다
 *   RequestFilter.MAX_LENGTH 가 컴파일 상수라 enum 인자에 쓸 수 있다.
 *   숫자를 여기 또 적으면 상한을 바꿀 때 한쪽만 고치게 된다.
 */
@Getter
@RequiredArgsConstructor
public enum GenerationErrorCode implements ErrorCode {

    EMPTY_REQUEST(HttpStatus.BAD_REQUEST, "GEN400-0",
            "요청 내용을 입력해 주세요."),

    REQUEST_TOO_LONG(HttpStatus.BAD_REQUEST, "GEN400-1",
            "요청이 너무 깁니다. " + RequestFilter.MAX_LENGTH + "자 이내로 줄여 주세요."),

    /**
     * ★ 400 이다. 404 가 아니다 — 못 찾은 건 주소가 가리키는 자원이 아니라 **본문에 실린 값**이다.
     *   `events.template_id` 가 가리키는 템플릿이 저장소에 없을 때도 여기로 온다.
     */
    TEMPLATE_NOT_FOUND(HttpStatus.BAD_REQUEST, "GEN400-2",
            "선택한 템플릿을 찾을 수 없습니다. 템플릿을 다시 골라 주세요."),

    /**
     * ★ 409 다. 400 이 아니다 — 요청은 멀쩡한데 지금은 안 될 뿐이다.
     *   버튼을 두 번 눌렀거나, 앞의 생성이 아직 도는 중이다.
     */
    ALREADY_GENERATING(HttpStatus.CONFLICT, "GEN409-0",
            "이미 생성이 진행 중입니다. 잠시 후 다시 시도해 주세요."),

    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "GEN404-0",
            "해당 작업을 찾을 수 없습니다. 이미 끝났거나 오래된 작업입니다."),

    PAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "GEN404-1",
            "아직 만들어진 페이지가 없습니다."),

    NOTHING_TO_CANCEL(HttpStatus.NOT_FOUND, "GEN404-2",
            "중단할 작업이 없습니다. 이미 끝났을 수 있습니다."),

    /**
     * ★ 이건 원래 **이벤트 도메인 코드다.**
     *   `EventErrorCode` 가 생기면 그쪽으로 옮기고 여기서 지운다.
     *   그때까지 생성 경로가 이벤트를 못 찾았을 때 던질 코드가 없어서 임시로 둔다.
     *   `deleted_at` 이 찍힌 이벤트도 여기로 온다 — "지워진 것" 과 "없는 것" 을
     *   구분해서 알려주면 지워진 이벤트가 있었다는 사실이 새어 나간다.
     */
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "GEN404-3",
            "이벤트를 찾을 수 없습니다."),

    /**
     * `REQ-EVT-10`. **종료 판정 기준은 `status = ENDED` 로 잡았다** —
     * 엔티티에 `EventStatus.ENDED` 가 실제로 있고, 그게 팀이 고른 기준이기 때문이다.
     * 이벤트 파트가 `end_date` 기준이라고 하면 EventStatusGuard 한 곳만 고치면 된다.
     */
    EVENT_ENDED(HttpStatus.CONFLICT, "GEN409-1",
            "종료된 이벤트는 페이지를 새로 만들 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
