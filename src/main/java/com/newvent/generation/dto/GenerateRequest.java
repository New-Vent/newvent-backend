package com.newvent.generation.dto;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 생성 요청 본문.
 *
 * ★ 제목·기간·참여링크가 빠졌다 — 이제 `events` 에서 읽는다
 */
public record GenerateRequest(

        @Schema(description = "생략하면 이벤트에 붙은 템플릿을 쓴다. 빈 문자열이면 템플릿 없이 requestText 로 AI 가 만든다.")
        @Size(max = 50, message = "템플릿 코드가 너무 깁니다.")
        String templateCode,

        @Schema(description = "AI 생성 요청문. 템플릿으로 만들 때는 비운다.")
        @Size(max = 500, message = "요청이 너무 깁니다. 500자 이내로 줄여 주세요.")
        String requestText,
        @Schema(description = "개인정보 확인으로 ASK_BACK 이 된 작업의 jobId. 확인 후 다시 보낼 때만 쓴다.")
        java.util.UUID privacyConfirmationJobId,
        @Schema(description = "개인정보가 들어가도 그대로 만들겠다는 관리자 확인. 생략하면 false")
        Boolean privacyConfirmed) {
    public GenerateRequest {
        privacyConfirmed = Boolean.TRUE.equals(privacyConfirmed);
    }
    public GenerateRequest(String templateCode, String requestText, java.util.UUID privacyConfirmationJobId) {
        this(templateCode, requestText, privacyConfirmationJobId, false);
    }
    public GenerateRequest(String templateCode, String requestText) {
        this(templateCode, requestText, null);
    }
}
