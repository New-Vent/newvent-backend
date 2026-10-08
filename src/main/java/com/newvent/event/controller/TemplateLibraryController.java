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
import com.newvent.common.response.PageResponse;
import com.newvent.event.dto.request.*;
import com.newvent.event.dto.response.*;
import com.newvent.event.service.TemplateLibraryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "템플릿 라이브러리", description = "기본 제공 템플릿은 모든 관리자가, 관리자가 등록한 템플릿은 등록한 관리자만 보고 쓴다. "
        + "남의 등록본은 없는 것과 같이 404 (EVENT404-1)")
@RestController
@Validated
@RequestMapping("/api/admin/template-library")
public class TemplateLibraryController {
    private final TemplateLibraryService library;

    public TemplateLibraryController(TemplateLibraryService library) {
        this.library = library;
    }

    @Operation(
            summary = "템플릿 목록",
            description = "keyword 는 이름 부분 일치(대소문자 무시, % _ ! 도 글자 그대로). builtin 을 비우면 전체, true 면 기본 제공, "
                    + "false 면 내가 등록한 것. 비활성은 includeInactive=true 일 때만 나온다. 최근 등록순이고 HTML 본문은 없다.")
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

    @Operation(
            summary = "템플릿 미리보기",
            description = "기간·참여 링크를 비운 HTML 조각을 준다. 관리자 화면에 바로 넣지 말고 격리된 iframe 으로 그린다. "
                    + "비활성인 내 등록본도 볼 수 있다.")
    @GetMapping("/{code}/preview")
    public ApiResponse<TemplatePreviewResponse> preview(
            @AuthenticationPrincipal AuthUser admin, @PathVariable String code) {
        return ApiResponse.success(library.preview(adminId(admin), code));
    }

    @Operation(
            summary = "저장 버전을 템플릿으로 등록",
            description = "내 이벤트의 저장된 버전만 등록할 수 있다 (남의 이벤트면 403 COMMON403-0). HTML 을 복사해 두므로 "
                    + "원본 이벤트를 고치거나 지워도 남는다. 기간·참여 링크는 비우고 허용하지 않은 스크립트는 뺀다. "
                    + "필수 블록이나 슬롯 구조가 맞지 않으면 400 (EVENT400-3), 이벤트가 없으면 404 (EVENT404-0), "
                    + "이 이벤트의 버전이 아니면 404 (EVENT404-3)")
    @PostMapping
    public ResponseEntity<ApiResponse<TemplateLibraryResponse>> register(
            @AuthenticationPrincipal AuthUser admin, @Valid @RequestBody TemplateRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(library.register(adminId(admin), request)));
    }

    @Operation(
            summary = "템플릿 이름·설명 수정",
            description = "HTML 은 바꾸지 않는다. description 을 비우거나 null 로 보내면 설명을 지운다. "
                    + "기본 제공 템플릿은 403 (EVENT403-0)")
    @PatchMapping("/{code}")
    public ApiResponse<TemplateLibraryResponse> update(@AuthenticationPrincipal AuthUser admin,
            @PathVariable String code, @Valid @RequestBody TemplateMetadataRequest request) {
        return ApiResponse.success(library.update(adminId(admin), code, request));
    }

    @Operation(
            summary = "템플릿 비활성화",
            description = "지우지 않고 새 이벤트에 고를 수 없게만 한다. 이미 이 템플릿을 쓰는 이벤트는 그대로다. "
                    + "다시 보내도 성공한다. 기본 제공 템플릿은 403 (EVENT403-0)")
    @DeleteMapping("/{code}")
    public ApiResponse<Void> deactivate(@AuthenticationPrincipal AuthUser admin, @PathVariable String code) {
        library.deactivate(adminId(admin), code);
        return ApiResponse.successNoData();
    }

    @Operation(
            summary = "템플릿으로 새 이벤트 만들기",
            description = "DRAFT 이벤트와 첫 버전을 한 번에 만든다. LLM 을 부르지 않고 게시하지도 않는다. "
                    + "응답의 eventId 로 에디터를 열고 versionId 를 편집 기준으로 쓴다. 이벤트명은 HTML 제목에 반영되지 않는다. "
                    + "비활성 템플릿은 404 (EVENT404-1), 종료일시가 시작일시보다 늦지 않으면 400 (EVENT400-0)")
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
