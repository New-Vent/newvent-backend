package com.newvent.generation.dto;

import com.newvent.generation.service.GenerationJob;

/**
 * 생성 시작 응답. jobId 를 받아 상태 폴링에 쓴다.
 */
public record GenerateStartResponse(String jobId, String phase, int percent) {

    public static GenerateStartResponse of(GenerationJob j) {
        return new GenerateStartResponse(
                j.jobId().toString(), j.phase().name(), j.phase().percent());
    }
}
