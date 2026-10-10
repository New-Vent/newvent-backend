package com.newvent.generation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.generation.dto.GenerationJobResponse;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.GenerationException;
import com.newvent.generation.service.GenerationJob;
import com.newvent.generation.service.GenerationJobStore;
import com.newvent.generation.service.GenerationService;

/**
 * 생성 진행 상태 조회 · 중단.
 *
 * ★ 이벤트 소유 관리자만 볼 수 있다. 작업을 찾기 전에 확인한다.
 */
@RestController
@RequestMapping("/api/admin/events/{eventId}/generate/{jobId}")
public class GenerationJobController {

    private final GenerationService generation;
    private final GenerationJobStore jobs;
    private final EventOwnerCheck ownerCheck;

    public GenerationJobController(GenerationService generation, GenerationJobStore jobs,
                                   EventOwnerCheck ownerCheck) {
        this.generation = generation;
        this.jobs = jobs;
        this.ownerCheck = ownerCheck;
    }

    /**
     * ★ 폴링용이다. 프론트가 1~2초마다 부른다.
     */
    @GetMapping
    public ApiResponse<GenerationJobResponse> status(@PathVariable Long eventId,
                                                     @PathVariable String jobId,
                                                     @AuthenticationPrincipal AuthUser admin) {
        ownerCheck.requireOwned(eventId, admin);
        return ApiResponse.success(GenerationJobResponse.of(find(eventId, jobId)));
    }

    /**
     * 중단.
     *
     * ★ 202 로 돌려준다. **아직 안 멈췄기 때문이다.**
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long eventId,
                                                    @PathVariable String jobId,
                                                    @AuthenticationPrincipal AuthUser admin) {
        ownerCheck.requireOwned(eventId, admin);
        // ★ 찾은 작업을 그대로 넘긴다. eventId 로 다시 찾으면 그 사이에 다른 작업이
        GenerationJob job = find(eventId, jobId);
        if (!generation.cancel(job)) {
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
