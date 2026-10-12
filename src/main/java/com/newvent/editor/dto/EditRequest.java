package com.newvent.editor.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 수정 요청 본문.
 *
 * @param requestText 관리자 요청문
 * @param blocks      미리보기에서 고른 영역(data-block key, 예: ["benefits"]). 선택.
 *                    없거나 비면 지금처럼 라우터가 요청문으로 영역까지 정한다.
 *                    ★ 4개까지 — 한 요청의 모델 호출 상한(라우터 1 + 영역 수 × 재시도)을 묶어 둔다
 */
public record EditRequest(

        @Schema(description = "어떻게 고칠지 적은 요청문. 500자까지.")
        @Size(max = 500, message = "요청이 너무 깁니다. 500자 이내로 줄여 주세요.")
        String requestText,
        @Schema(description = "개인정보 확인으로 ASK_BACK 된 작업의 jobId. 확인 후 다시 보낼 때만 쓴다.")
        java.util.UUID privacyConfirmationJobId,
        @Schema(description = "개인정보가 들어 있어도 진행한다고 확인했는지. 없으면 false.")
        Boolean privacyConfirmed,

        @Schema(description = "미리보기에서 고른 영역의 data-block 키 (예: [\"benefits\"]). 네 개까지. 없거나 비면 요청문으로 영역을 정한다.")
        @Size(max = 4, message = "영역은 네 개까지 고를 수 있습니다.")
        List<String> blocks) {
    public EditRequest {
        privacyConfirmed = Boolean.TRUE.equals(privacyConfirmed);
    }
    public EditRequest(String requestText, java.util.UUID privacyConfirmationJobId) {
        this(requestText, privacyConfirmationJobId, false, null);
    }
    public EditRequest(String requestText) { this(requestText, null); }
}
