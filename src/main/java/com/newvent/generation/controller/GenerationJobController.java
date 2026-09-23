package com.newvent.generation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.newvent.common.response.ApiResponse;
import com.newvent.generation.dto.GenerationJobResponse;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.GenerationException;
import com.newvent.generation.service.GenerationJob;
import com.newvent.generation.service.GenerationJobStore;
import com.newvent.generation.service.GenerationService;

/**
 * 생성 진행 상태 조회 · 중단.
 */
@RestController
@RequestMapping("/api/admin/events/{eventId}/generate/{jobId}")
public class GenerationJobController {

    private final GenerationService generation;
    private final GenerationJobStore jobs;

    public GenerationJobController(GenerationService generation, GenerationJobStore jobs) {
        this.generation = generation;
        this.jobs = jobs;
    }

    /**
     * ★ 폴링용이다. 프론트가 1~2초마다 부른다.
     */
    @GetMapping
    public ApiResponse<GenerationJobResponse> status(@PathVariable Long eventId,
                                                     @PathVariable String jobId) {
        return ApiResponse.success(GenerationJobResponse.of(find(eventId, jobId)));
    }

    /**
     * 중단.
     *
     * ★ 202 로 돌려준다. **아직 안 멈췄기 때문이다.**
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long eventId,
                                                    @PathVariable String jobId) {
        GenerationJob job = find(eventId, jobId);
        if (job.done() || !generation.cancel(eventId)) {
            throw new GenerationException(GenerationErrorCode.NOTHING_TO_CANCEL);
        }
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.successNoData(
                        "중단을 요청했습니다. 진행 중인 단계가 끝나면 멈춥니다."));
    }

    private GenerationJob find(Long eventId, String jobId) {
        return jobs.byId(jobId)
                .filter(j -> j.eventId().equals(eventId))
                .orElseThrow(() -> new GenerationException(GenerationErrorCode.JOB_NOT_FOUND));
    }
}
