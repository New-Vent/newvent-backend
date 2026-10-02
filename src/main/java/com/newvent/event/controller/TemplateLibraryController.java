package com.newvent.event.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.event.dto.request.*;
import com.newvent.event.dto.response.*;
import com.newvent.event.service.TemplateLibraryService;

@RestController
@Validated
@RequestMapping("/api/admin/template-library")
public class TemplateLibraryController {
    private final TemplateLibraryService library;

    public TemplateLibraryController(TemplateLibraryService library) {
        this.library = library;
    }

    @GetMapping
    public ApiResponse<PageResponse<TemplateLibraryResponse>> list(
            @AuthenticationPrincipal AuthUser admin,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) Boolean builtin,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return ApiResponse.success(library.list(adminId(admin), keyword, builtin, includeInactive, page, size));
    }

    @GetMapping("/{code}/preview")
    public ApiResponse<TemplatePreviewResponse> preview(
            @AuthenticationPrincipal AuthUser admin, @PathVariable String code) {
        return ApiResponse.success(library.preview(adminId(admin), code));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemplateLibraryResponse>> register(
            @AuthenticationPrincipal AuthUser admin, @Valid @RequestBody TemplateRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(library.register(adminId(admin), request)));
    }

    @PatchMapping("/{code}")
    public ApiResponse<TemplateLibraryResponse> update(@AuthenticationPrincipal AuthUser admin,
            @PathVariable String code, @Valid @RequestBody TemplateMetadataRequest request) {
        return ApiResponse.success(library.update(adminId(admin), code, request));
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> deactivate(@AuthenticationPrincipal AuthUser admin, @PathVariable String code) {
        library.deactivate(adminId(admin), code);
        return ApiResponse.successNoData();
    }

    @PostMapping("/{code}/events")
    public ResponseEntity<ApiResponse<TemplateUseResponse>> use(@AuthenticationPrincipal AuthUser admin,
            @PathVariable String code, @Valid @RequestBody TemplateUseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(library.use(adminId(admin), code, request)));
    }

    private Long adminId(AuthUser admin) {
        if (admin == null || !admin.admin()) throw new AccessDeniedException("관리자만 사용할 수 있습니다.");
        return admin.id();
    }
}
