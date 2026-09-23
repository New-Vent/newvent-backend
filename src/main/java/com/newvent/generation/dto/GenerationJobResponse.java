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
        String message) {

    public static GenerationJobResponse of(GenerationJob j) {
        return new GenerationJobResponse(
                j.jobId().toString(),
                j.phase().name(),
                j.phase().label(),
                j.phase().percent(),
                j.done(),
                j.attempt() > 0 ? j.attempt() : null,
                j.message());
    }
}
