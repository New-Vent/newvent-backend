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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 생성 진행 상태 조회 · 중단.
 */
@Tag(name = "페이지 생성 작업", description = "생성 시작이 돌려준 jobId 로 진행 상황을 보고 중단한다. "
        + "작업은 서버 메모리에 30분만 남아 그 뒤나 서버 재시작 후에는 404 (GEN404-0). "
        + "jobId 가 이 이벤트의 작업이 아니어도 404 (GEN404-0)")
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
    @Operation(
            summary = "생성 진행 상황",
            description = "1~2초마다 부르고 done 이 true 면 멈춘다. phase 가 DONE 이면 versionId 로 결과 버전을 연다. "
                    + "ASK_BACK 은 실패가 아니라 관리자 확인이 필요한 상태다 — privacyConfirmationRequired 가 true 면 "
                    + "privacyTypes 를 보여 주고 확인을 받아 다시 시작한다. FAILED 의 message 는 화면에 그대로 보여 줘도 된다.")
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
    @Operation(
            summary = "생성 중단",
            description = "바로 멈추지 않고 진행 중인 단계가 끝나면 멈추므로 202 다. 멈췄는지는 진행 상황에서 CANCELLED 로 확인한다. "
                    + "이미 끝난 작업이면 404 (GEN404-2)")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long eventId,
                                                    @PathVariable String jobId) {
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
