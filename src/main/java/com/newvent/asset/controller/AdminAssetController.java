package com.newvent.asset.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.newvent.asset.dto.AssetResponse;
import com.newvent.asset.service.AssetService;
import com.newvent.common.response.ApiResponse;

/**
 * 이벤트에 이미지를 올린다 · 본다 · 지운다. **관리자만** (SecurityConfig: /api/admin/** ROLE_ADMIN)
 */
@RestController
@RequestMapping("/api/admin/events/{eventId}/assets")
public class AdminAssetController {

    private final AssetService assets;

    public AdminAssetController(AssetService assets) {
        this.assets = assets;
    }

    /** @param caption 관리자가 적는 설명. alt 로 쓴다. 모델이 지어내게 두지 않는다 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AssetResponse> upload(@PathVariable Long eventId,
                                             @RequestPart("file") MultipartFile file,
                                             @RequestParam(required = false) String caption) {
        return ApiResponse.success(assets.upload(eventId, file, caption));
    }

    @GetMapping
    public ApiResponse<List<AssetResponse>> list(@PathVariable Long eventId) {
        return ApiResponse.success(assets.list(eventId));
    }

    @DeleteMapping("/{assetId}")
    public ApiResponse<Void> delete(@PathVariable Long eventId, @PathVariable String assetId) {
        assets.delete(eventId, assetId);
        return ApiResponse.successNoData();
    }
}
