package com.newvent.generation.controller;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.common.response.PageResponse;
import com.newvent.generation.dto.LlmCallLogDetailResponse;
import com.newvent.generation.dto.LlmCallLogSummaryResponse;
import com.newvent.generation.service.LlmCallLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * LLM 호출 로그 조회 (관리자). 쓰기는 LlmCallLogService.record* 가 담당한다.
 */
@Tag(name = "LLM 호출 로그", description = "페이지 생성·채팅 수정이 모델을 부른 기록. 조회만 하고, 기록은 서버가 호출할 때마다 남긴다.")
@Validated
@RestController
@RequestMapping("/api/admin/llm-calls")
public class AdminLlmCallLogController {

    private final LlmCallLogService logs;

    public AdminLlmCallLogController(LlmCallLogService logs) {
        this.logs = logs;
    }

    /** 목록. eventId·callOk·기간 필터 가능, 생성 시각 내림차순. */
    @Operation(
            summary = "LLM 호출 로그 목록",
            description = "최신순. eventId·callOk(모델 호출 성공 여부)·생성 시각 구간(from~to)으로 거를 수 있고 비운 조건은 무시한다. "
                    + "재시도는 시도마다 한 줄씩 남는다.")
    @GetMapping
    public ApiResponse<PageResponse<LlmCallLogSummaryResponse>> list(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Boolean callOk,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(logs.search(eventId, callOk, from, to, page, size));
    }

    /** 상세 1건. 실패 분류·잘림·RAG 청크 아이디 포함. */
    @Operation(
            summary = "LLM 호출 로그 상세",
            description = "목록에 없는 실패 분류(failureType)·검증 실패 코드·잘림 여부·참고한 RAG 청크 ID 가 더해진다. "
                    + "없으면 404 (LLM404-0)")
    @GetMapping("/{id}")
    public ApiResponse<LlmCallLogDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(logs.detail(id));
    }
}
