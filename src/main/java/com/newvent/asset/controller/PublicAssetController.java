package com.newvent.asset.controller;

import java.io.InputStream;
import java.time.Duration;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.asset.domain.EventAsset;
import com.newvent.asset.exception.AssetErrorCode;
import com.newvent.asset.exception.AssetException;
import com.newvent.asset.service.AssetService;


/**
 * 올린 이미지를 내려 준다. **비로그인으로 열려야 한다** — 게시 페이지(/e/*)를 일반 방문자가 본다.
 *
 * ★ 그래서 assetId 가 추측 불가능해야 한다(UUID 32자리). 1 · 2 · 3 이면 남의 이벤트가 다 열린다.
 * ★ ApiResponse 로 감싸지 않는다. &lt;img src&gt; 가 바로 받는 자리라 바이트가 그대로 나가야 한다.
 */
@RestController
@RequestMapping("/api/public/assets")
public class PublicAssetController {

    private final AssetService assets;

    public PublicAssetController(AssetService assets) {
        this.assets = assets;
    }

    @GetMapping("/{file}")
    public ResponseEntity<InputStreamResource> get(@PathVariable String file) {
        // 주소는 "{id}.{ext}" 다. 확장자는 보기 좋으라고 붙은 것이고 찾는 기준은 id 다
        String assetId = file.contains(".") ? file.substring(0, file.indexOf('.')) : file;

        EventAsset a = assets.find(assetId)
                .orElseThrow(() -> new AssetException(AssetErrorCode.NOT_FOUND));
        InputStream in = assets.open(a)
                .orElseThrow(() -> new AssetException(AssetErrorCode.NOT_FOUND));

        return ResponseEntity.ok()
                .header("Content-Type", a.getContentType())
                .contentLength(a.getByteSize())
                // 내용이 바뀌지 않는 주소라 길게 잡는다. 바꾸려면 새로 올려서 새 주소를 쓴다
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new InputStreamResource(in));
    }
}
