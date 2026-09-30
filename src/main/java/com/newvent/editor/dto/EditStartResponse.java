package com.newvent.editor.dto;

import com.newvent.generation.service.GenerationJob;

/**
 * 수정 시작 응답. jobId 를 받아 상태 폴링에 쓴다.
 */
public record EditStartResponse(String jobId, String phase, int percent) {

    public static EditStartResponse of(GenerationJob j) {
        return new EditStartResponse(
                j.jobId().toString(), j.phase().name(), j.phase().percent());
    }
}
