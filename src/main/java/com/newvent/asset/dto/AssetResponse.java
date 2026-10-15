package com.newvent.asset.dto;

import com.newvent.asset.domain.EventAsset;

/**
 * @param scrim 사진 위 글자를 읽히게 하는 덮개 농도. 프론트가 --ev-asset-scrim 에 넣는다
 */
public record AssetResponse(
        String assetId,
        String url,
        String contentType,
        long byteSize,
        double scrim,
        String caption) {

    public static AssetResponse from(EventAsset a) {
        return new AssetResponse(a.getId(), a.getUrl(), a.getContentType(),
                a.getByteSize(), a.getScrim(), a.getCaption());
    }
}
