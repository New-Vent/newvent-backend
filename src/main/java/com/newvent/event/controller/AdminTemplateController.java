package com.newvent.event.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.event.dto.response.TemplateResponse;
import com.newvent.event.service.EventTemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "기본 제공 템플릿", description = "활성인 기본 제공 템플릿의 메타데이터만 준다. "
        + "관리자 등록본과 미리보기 HTML 은 템플릿 라이브러리 API 에서 본다.")
@RestController
@RequestMapping("/api/admin/templates")
public class AdminTemplateController {

    private final EventTemplateService eventTemplateService;

    public AdminTemplateController(EventTemplateService eventTemplateService) {
        this.eventTemplateService = eventTemplateService;
    }

    @Operation(summary = "기본 제공 템플릿 목록", description = "등록순으로 전부 준다. 페이지를 나누지 않는다.")
    @GetMapping
    public ApiResponse<List<TemplateResponse>> list() {
        return ApiResponse.success(eventTemplateService.findActiveTemplates());
    }

    @Operation(summary = "기본 제공 템플릿 상세", description = "없거나 비활성이거나 관리자 등록본이면 404 (EVENT404-1)")
    @GetMapping("/{templateKey}")
    public ApiResponse<TemplateResponse> detail(@PathVariable String templateKey) {
        return ApiResponse.success(eventTemplateService.findByKey(templateKey));
    }
}
