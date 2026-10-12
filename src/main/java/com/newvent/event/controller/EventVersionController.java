package com.newvent.event.controller;

import jakarta.validation.Valid;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.event.dto.request.DirectEditRequest;
import com.newvent.event.dto.response.DirectEditResponse;
import com.newvent.event.dto.response.EventVersionDetailResponse;
import com.newvent.event.dto.response.EventVersionListResponse;
import com.newvent.event.service.DirectEditService;
import com.newvent.event.service.EventVersionService;
import com.newvent.event.service.VersionRestoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "이벤트 버전", description = "버전 이력·저장 지점·직접 편집·되돌리기. 이벤트를 만든 관리자만 쓸 수 있다. "
        + "남의 이벤트면 조회·저장 지점은 404 (EVENT404-2), 직접 편집·되돌리기는 403 (COMMON403-0). "
        + "이벤트가 없거나 휴지통에 있으면 404 (EVENT404-0)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/events/{eventId}/versions")
public class EventVersionController {

    private final EventVersionService eventVersionService;
    private final DirectEditService directEditService;
    private final VersionRestoreService versionRestoreService;

    @Operation(
            summary = "직접 편집",
            description = "기준 버전(sourceVersionId)의 문구나 참여 버튼 스타일을 고쳐 새 버전으로 저장한다. 기준 버전은 그대로 남는다. "
                    + "문구도 버튼 스타일도 없으면 400, 화면이 최신이 아니면 409 (DIRECT_EDIT409-0) — 새로고침 후 다시 한다. "
                    + "종료됐거나 게시 중인데 종료일시가 지났으면 409 (EVENT409-1), 이 이벤트의 버전이 아니면 404 (EVENT404-3)")
    @PostMapping("/direct-edit")
    public ApiResponse<DirectEditResponse> directEdit(
            @PathVariable Long eventId,
            @Valid @RequestBody DirectEditRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                directEditService.directEdit(eventId, request, adminId(authentication)));
    }

    /**
     * 이 버전으로 되돌린다. <b>새 버전이 만들어진다.</b>
     *
     * ★ POST 다. 멱등이 아니다 — 두 번 누르면 버전이 두 개 생긴다.
     *   내용은 같지만 이력에 두 줄이 남는다. 그게 사실이므로 PUT 이 아니다.
     *
     * ★ 본문이 없다. 되돌릴 대상은 경로에 다 있다.
     */
    @Operation(
            summary = "이 버전으로 되돌리기",
            description = "그 버전의 HTML 로 새 버전을 만든다. 멱등이 아니라 두 번 보내면 버전이 두 개 생긴다. 본문은 없다. "
                    + "종료됐거나 게시 중인데 종료일시가 지났으면 409 (EVENT409-1), 이 이벤트의 버전이 아니면 404 (EVENT404-3)")
    @PostMapping("/{versionId}/restore")
    public ApiResponse<DirectEditResponse> restore(
            @PathVariable Long eventId,
            @PathVariable Long versionId,
            Authentication authentication
    ) {
        return ApiResponse.success(
                versionRestoreService.restore(eventId, versionId, adminId(authentication)));
    }

    /**
     * 인증에서 관리자 id 를 꺼낸다.
     *
     * ★ 쓰기 엔드포인트마다 같은 네 줄을 복사하던 것을 한 군데로 모은다.
     *   복사본이 늘면 그중 하나에서 {@code user.admin()} 검사를 빠뜨리기 쉽다.
     */
    private static Long adminId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)
                || !user.admin()) {
            throw new AccessDeniedException("관리자 인증이 필요합니다.");
        }
        return user.id();
    }

    @Operation(
            summary = "버전 이력",
            description = "자동 저장 버전과 저장 지점을 모두 최신 번호순으로 보여 준다. 저장 지점인지는 checkpoint, "
                    + "지금 게시 중인 버전인지는 published 로 구분한다. 버전이 없으면 빈 목록이다.")
    @GetMapping
    public ApiResponse<EventVersionListResponse> getVersions(
        @PathVariable Long eventId,
        Authentication authentication
    ) {
        return ApiResponse.success(eventVersionService.getVersions(eventId, adminId(authentication)));
    }

    @Operation(
            summary = "저장 지점 지정",
            description = "새 버전을 만들지 않고 표시만 한다. 이미 지정된 버전에 다시 보내도 성공한다. "
                    + "이 이벤트의 버전이 아니면 404 (EVENT404-3)")
    @PutMapping("/{versionId}/checkpoint")
    public ApiResponse<Void> markCheckpoint(
            @PathVariable Long eventId,
            @PathVariable Long versionId,
            Authentication authentication
    ) {
        eventVersionService.markCheckpoint(eventId, versionId, adminId(authentication));
        return ApiResponse.successNoData();
    }

    @Operation(
            summary = "저장 지점 해제",
            description = "표시만 지우고 버전은 남는다. 이미 해제된 버전에 다시 보내도 성공한다. "
                    + "지금 게시 중인 버전은 해제할 수 없다 (409 EVENT409-0). 이 이벤트의 버전이 아니면 404 (EVENT404-3)")
    @DeleteMapping("/{versionId}/checkpoint")
    public ApiResponse<Void> unmarkCheckpoint(
            @PathVariable Long eventId,
            @PathVariable Long versionId,
            Authentication authentication
    ) {
        eventVersionService.unmarkCheckpoint(eventId, versionId, adminId(authentication));
        return ApiResponse.successNoData();
    }

    @Operation(
            summary = "버전 상세",
            description = "그 버전의 HTML 원문(htmlContent)을 미리보기용으로 준다. 저장 지점이 아닌 버전도 볼 수 있다. "
                    + "sandbox iframe 으로 그리고 allow-same-origin 은 주지 않는다. 이 이벤트의 버전이 아니면 404 (EVENT404-3)")
    @GetMapping("/{versionId}")
    public ApiResponse<EventVersionDetailResponse> getVersion(
            @PathVariable Long eventId,
            @PathVariable Long versionId,
            Authentication authentication
    ) {
        return ApiResponse.success(eventVersionService.getVersion(eventId, versionId, adminId(authentication)));
    }
}
