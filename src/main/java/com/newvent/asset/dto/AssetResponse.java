package com.newvent.asset.dto;

import com.newvent.asset.domain.EventAsset;

/**
 * @param seq   이벤트별 고정 번호. 화면이 "1" · "2" 로 보여 주고 관리자가 그 번호로 말한다
 * @param scrim 사진 위 글자를 읽히게 하는 덮개 농도. 프론트가 --ev-asset-scrim 에 넣는다
 */
public record AssetResponse(
        String assetId,
        int seq,
        String url,
        String contentType,
        long byteSize,
        double scrim,
        String caption) {

    public static AssetResponse from(EventAsset a) {
        return new AssetResponse(a.getId(), a.getSeq(), a.getUrl(), a.getContentType(),
                a.getByteSize(), a.getScrim(), a.getCaption());
    }
}
