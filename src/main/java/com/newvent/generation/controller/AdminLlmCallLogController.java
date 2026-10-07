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

/**
 * LLM 호출 로그 조회 (관리자). 쓰기는 LlmCallLogService.record* 가 담당한다.
 */
@Validated
@RestController
@RequestMapping("/api/admin/llm-calls")
public class AdminLlmCallLogController {

    private final LlmCallLogService logs;

    public AdminLlmCallLogController(LlmCallLogService logs) {
        this.logs = logs;
    }

    /** 목록. eventId·callOk·기간 필터 가능, 생성 시각 내림차순. */
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
    @GetMapping("/{id}")
    public ApiResponse<LlmCallLogDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(logs.detail(id));
    }
}
