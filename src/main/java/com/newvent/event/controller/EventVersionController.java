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

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/events/{eventId}/versions")
public class EventVersionController {

    private final EventVersionService eventVersionService;
    private final DirectEditService directEditService;
    private final VersionRestoreService versionRestoreService;

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

    @GetMapping
    public ApiResponse<EventVersionListResponse> getVersions(
        @PathVariable Long eventId,
        Authentication authentication
    ) {
        adminId(authentication);
        return ApiResponse.success(eventVersionService.getVersions(eventId));
    }

    @PutMapping("/{versionId}/checkpoint")
    public ApiResponse<Void> markCheckpoint(
            @PathVariable Long eventId,
            @PathVariable Long versionId,
            Authentication authentication
    ) {
        adminId(authentication);
        eventVersionService.markCheckpoint(eventId, versionId);
        return ApiResponse.successNoData();
    }

    @DeleteMapping("/{versionId}/checkpoint")
    public ApiResponse<Void> unmarkCheckpoint(
            @PathVariable Long eventId,
            @PathVariable Long versionId,
            Authentication authentication
    ) {
        adminId(authentication);
        eventVersionService.unmarkCheckpoint(eventId, versionId);
        return ApiResponse.successNoData();
    }

    @GetMapping("/{versionId}")
    public ApiResponse<EventVersionDetailResponse> getVersion(
            @PathVariable Long eventId,
            @PathVariable Long versionId,
            Authentication authentication
    ) {
        adminId(authentication);
        return ApiResponse.success(eventVersionService.getVersion(eventId, versionId));
    }
}
