package com.newvent.generation.service;

import java.util.Optional;

import com.newvent.generation.exception.GenerationErrorCode;

/**
 * 모델에 보내기 전에 관리자 입력을 거른다.
 *
 * ★ 여기서 거르는 건 **우리 비용을 아끼는 것**이지 보안이 아니다.
 *   프롬프트 주입을 글자 검사로 막을 수 없다. 그건 출력 쪽에서 막는다 —
 *   sanitizeGenerated 가 script·onclick·data-slot 을 걷어내고,
 *   BlockValidator 가 형태를 본다. 입력 필터는 그 앞의 체가 아니라 **문지기**다.
 *
 * ★ 거부 사유는 사용자에게 그대로 보여줄 문장이다.
 *   "입력값이 유효하지 않습니다" 같은 말은 관리자가 뭘 고쳐야 할지 모른다.
 */
public final class RequestFilter {

    /**
     * 요청문 최대 글자 수.
     */
    public static final int MAX_LENGTH = 500;

    private RequestFilter() {}

    /**
     * 거부할 이유가 있으면 그 코드, 없으면 빈 값.
     */
    public static Optional<GenerationErrorCode> reject(String text) {
        if (text == null || text.isBlank()) {
            return Optional.of(GenerationErrorCode.EMPTY_REQUEST);
        }
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            return Optional.of(GenerationErrorCode.EMPTY_REQUEST);
        }
        if (cleaned.length() > MAX_LENGTH) {
            return Optional.of(GenerationErrorCode.REQUEST_TOO_LONG);
        }
        return Optional.empty();
    }

    /**
     * 모델에 보낼 형태로 다듬는다. **거부와 다듬기는 다른 일이다.**
     */
    public static String clean(String text) {
        if (text == null) return "";
        return text
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "")   // 제어문자 (줄바꿈·탭 제외)
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "") // 폭 없는 공백 · BOM
                .replaceAll("\n{3,}", "\n\n")               // 빈 줄 3개 이상 → 2개
                .strip();
    }
}
