package com.newvent.generation.dto;

import com.newvent.generation.service.GenerationJob;

/**
 * 생성 진행 상태.
 *
 * ★ message 에 내부 오류 원문이 들어가면 안 된다
 */
public record GenerationJobResponse(
        String jobId,
        String phase,
        String label,
        int percent,
        boolean done,
        Integer attempt,
        String message,

        /**
         * 이 작업이 만든 버전의 id. phase 가 SAVING 을 지나기 전에는 null
         */
        Long versionId) {

    public static GenerationJobResponse of(GenerationJob j) {
        return new GenerationJobResponse(
                j.jobId().toString(),
                j.phase().name(),
                j.phase().label(),
                j.phase().percent(),
                j.done(),
                j.attempt() > 0 ? j.attempt() : null,
                j.message(),
                j.versionId());
    }
}
