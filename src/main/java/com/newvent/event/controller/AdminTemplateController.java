package com.newvent.event.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.event.dto.TemplateResponse;
import com.newvent.event.service.EventTemplateService;

@RestController
@RequestMapping("/api/admin/templates")
public class AdminTemplateController {

    private final EventTemplateService eventTemplateService;

    public AdminTemplateController(EventTemplateService eventTemplateService) {
        this.eventTemplateService = eventTemplateService;
    }

    @GetMapping
    public ApiResponse<List<TemplateResponse>> list() {
        return ApiResponse.success(eventTemplateService.findActiveTemplates());
    }

    @GetMapping("/{templateKey}")
    public ApiResponse<TemplateResponse> detail(@PathVariable String templateKey) {
        return ApiResponse.success(eventTemplateService.findByKey(templateKey));
    }
}
