package com.newvent.generation.dto;

/**
 * 미리보기 HTML. API 규약 §2 에 따라 ApiResponse<PreviewResponse> 로 감싸 나간다.
 *
 * ★ 슬롯은 채워진 상태다
 *   저장본에는 기간·참여링크가 비어 있고, 보여줄 때 Slots.fill() 이 채운다.
 */
public record PreviewResponse(String html) {

    public static PreviewResponse of(String html) {
        return new PreviewResponse(html);
    }
}
