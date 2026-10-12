package com.newvent.generation.dto;

import com.newvent.generation.service.GenerationJob;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 생성 진행 상태.
 *
 * ★ message 에 내부 오류 원문이 들어가면 안 된다
 */
public record GenerationJobResponse(
        String jobId,
        @Schema(description = "진행 단계. DONE·FAILED·ASK_BACK·CANCELLED 이면 끝난 것이다.",
                allowableValues = {"QUEUED", "PREPARING", "ROUTING", "CALLING", "VALIDATING", "SAVING",
                        "DONE", "FAILED", "ASK_BACK", "CANCELLED"})
        String phase,
        @Schema(description = "화면에 보여 줄 단계 이름")
        String label,
        @Schema(description = "단계별로 정해진 진행률(0~100)")
        int percent,
        @Schema(description = "true 면 폴링을 멈춘다")
        boolean done,
        @Schema(description = "모델 호출 시도 번호. 아직 부르지 않았으면 null")
        Integer attempt,
        @Schema(description = "실패·되묻기 때 화면에 보여 줄 안내 문구")
        String message,

        /**
         * 이 작업이 만든 버전의 id. phase 가 SAVING 을 지나기 전에는 null
         */
        @Schema(description = "만들어진 버전 ID. 저장이 끝나기 전에는 null")
        Long versionId,
        @Schema(description = "요청문에 개인정보가 보여 관리자 확인이 필요하면 true")
        boolean privacyConfirmationRequired,
        @Schema(description = "발견한 개인정보 종류")
        java.util.List<String> privacyTypes) {

    public static GenerationJobResponse of(GenerationJob j) {
        return new GenerationJobResponse(
                j.jobId().toString(),
                j.phase().name(),
                j.phase().label(),
                j.phase().percent(),
                j.done(),
                j.attempt() > 0 ? j.attempt() : null,
                j.message(),
                j.versionId(), j.privacyConfirmationRequired(), j.privacyTypes());
    }
}
