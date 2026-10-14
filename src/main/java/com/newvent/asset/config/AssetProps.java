package com.newvent.asset.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param store         local | s3 — 지금은 local 뿐이다
 * @param dir           local 일 때 저장 경로. 도커 볼륨이 여기에 붙는다
 * @param publicBaseUrl 주소 앞부분. **절대 URL 이어야 한다** — 정화기가 상대 경로를 못 받는다
 * @param maxBytes      한 장 상한. GIF 는 같은 화질 mp4 의 10~20배라 상한이 꼭 필요하다
 * @param maxPerEvent   한 이벤트 상한. 박스 디스크가 유한하다
 */
@ConfigurationProperties(prefix = "asset")
public record AssetProps(
        String store,
        String dir,
        String publicBaseUrl,
        long maxBytes,
        int maxPerEvent) {

    public AssetProps {
        if (store == null || store.isBlank()) store = "local";
        if (dir == null || dir.isBlank()) dir = "/data/assets";
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) publicBaseUrl = "http://localhost:8080";
        if (maxBytes <= 0) maxBytes = 3L * 1024 * 1024;
        if (maxPerEvent <= 0) maxPerEvent = 20;
    }
}
