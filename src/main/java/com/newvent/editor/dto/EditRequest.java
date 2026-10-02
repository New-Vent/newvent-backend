package com.newvent.editor.dto;

import jakarta.validation.constraints.Size;

/**
 * 수정 요청 본문.
 */
public record EditRequest(

        @Size(max = 500, message = "요청이 너무 깁니다. 500자 이내로 줄여 주세요.")
        String requestText,
        java.util.UUID privacyConfirmationJobId,
        Boolean privacyConfirmed) {
    public EditRequest {
        privacyConfirmed = Boolean.TRUE.equals(privacyConfirmed);
    }
    public EditRequest(String requestText, java.util.UUID privacyConfirmationJobId) {
        this(requestText, privacyConfirmationJobId, false);
    }
    public EditRequest(String requestText) { this(requestText, null); }
}
