package com.newvent.asset.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.newvent.asset.service.AssetStore;
import com.newvent.asset.service.LocalAssetStore;

/**
 * AssetStore 구현을 하나 고른다. **여기가 유일한 분기점이다** — LlmConfig 와 같은 모양.
 *
 * ★ S3 로 옮길 때 고칠 곳은 여기 한 줄과 S3AssetStore 하나다.
 *   업로드 API · 정화기 · 프론트는 한 글자도 안 바뀐다.
 */
@Configuration
@EnableConfigurationProperties(AssetProps.class)
public class AssetConfig {

    /**
     * ★ prod 에서 이미지 주소가 잘못돼 있으면 기동을 막는다.
     *   주소가 DB 와 게시된 페이지에 박히므로, 틀린 채로 뜨면 되돌릴 수 없다.
     *   SecurityConfig 가 ProdAuthGuard 를 끌어오는 것과 같은 모양이다.
     */
    @Bean
    @Profile("prod")
    public ProdAssetGuard prodAssetGuard(AssetProps p) {
        return new ProdAssetGuard(p);
    }

    @Bean
    public AssetStore assetStore(AssetProps p) {
        return switch (p.store()) {
            case "local" -> new LocalAssetStore(Path.of(p.dir()), p.publicBaseUrl());
            default -> throw new IllegalStateException(
                    "모르는 asset.store: " + p.store() + " (local)");
        };
    }
}
