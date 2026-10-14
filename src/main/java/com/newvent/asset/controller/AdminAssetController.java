package com.newvent.asset.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.newvent.asset.dto.AssetResponse;
import com.newvent.asset.service.AssetService;
import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.event.service.EventOwnerCheck;

/**
 * 이벤트에 이미지를 올린다 · 본다 · 지운다.
 *
 * ★★ 세 군데 모두 {@link EventOwnerCheck} 를 **먼저** 부른다.
 *   SecurityConfig 의 {@code /api/admin/** → ROLE_ADMIN} 은 "관리자인가" 만 본다.
 *   "이 이벤트의 관리자인가" 는 안 본다 — 그것만 믿으면 아무 관리자나 eventId 만 알면
 *   남의 이벤트 이미지를 올리고 · 보고 · 지울 수 있다.
 *   generation · rag 컨트롤러가 같은 자리에서 같은 검사를 한다.
 *
 * ★ 서비스가 아니라 컨트롤러에서 보는 이유 — AssetService 는 eventId 만 받는 순수한 저장소다.
 *   인증 주체를 거기까지 끌고 내려가면 EditService · GenerationService 도 다 따라와야 한다.
 */
@RestController
@RequestMapping("/api/admin/events/{eventId}/assets")
public class AdminAssetController {

    private final AssetService assets;
    private final EventOwnerCheck ownerCheck;

    public AdminAssetController(AssetService assets, EventOwnerCheck ownerCheck) {
        this.assets = assets;
        this.ownerCheck = ownerCheck;
    }

    /** @param caption 관리자가 적는 설명. alt 로 쓴다. 모델이 지어내게 두지 않는다 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AssetResponse> upload(@PathVariable Long eventId,
                                             @RequestPart("file") MultipartFile file,
                                             @RequestParam(required = false) String caption,
                                             @AuthenticationPrincipal AuthUser admin) {
        ownerCheck.requireOwned(eventId, admin);
        return ApiResponse.success(assets.upload(eventId, file, caption));
    }

    @GetMapping
    public ApiResponse<List<AssetResponse>> list(@PathVariable Long eventId,
                                                 @AuthenticationPrincipal AuthUser admin) {
        ownerCheck.requireOwned(eventId, admin);
        return ApiResponse.success(assets.list(eventId));
    }

    @DeleteMapping("/{assetId}")
    public ApiResponse<Void> delete(@PathVariable Long eventId,
                                    @PathVariable String assetId,
                                    @AuthenticationPrincipal AuthUser admin) {
        ownerCheck.requireOwned(eventId, admin);
        assets.delete(eventId, assetId);
        return ApiResponse.successNoData();
    }
}
