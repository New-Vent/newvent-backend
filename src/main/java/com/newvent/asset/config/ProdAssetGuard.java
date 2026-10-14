package com.newvent.asset.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.context.annotation.Profile;

/**
 * prod 에서 이미지 주소가 잘못 잡혀 있으면 <b>기동을 막는다.</b> ProdAuthGuard 와 같은 자세다.
 *
 * ★★ 왜 조용히 뜨면 안 되나 — <b>주소가 DB 에 박힌다.</b>
 *   업로드 시점에 {@code asset.public-base-url} 로 완성한 주소를 event_assets.url 에 저장하고,
 *   게시된 페이지의 &lt;img src&gt; 에도 그 글자가 그대로 들어간다.
 *   기본값(http://localhost:8080)으로 뜨면 그동안 올린 이미지가 **전부 못 쓰게 된다** —
 *   나중에 환경변수를 고쳐도 이미 저장된 행과 이미 게시된 페이지는 안 고쳐진다.
 *   되돌리려면 DB 를 직접 손봐야 한다. 그러느니 안 뜨는 게 낫다.
 *
 * ★ http 도 막는다. 서비스가 https 라 브라우저가 혼합 콘텐츠로 **이미지만 차단**한다.
 *   페이지는 멀쩡히 뜨고 사진만 안 보여서 원인을 찾기 어렵다.
 *
 * ★ 끝 슬래시는 LocalAssetStore 가 떼므로 여기서는 보지 않는다.
 */
@Profile("prod")
public final class ProdAssetGuard {

    public ProdAssetGuard(AssetProps props) {
        String base = props.publicBaseUrl();
        if (base == null || base.isBlank() || base.contains("${")) {
            throw invalid("ASSET_PUBLIC_BASE_URL 이 없습니다");
        }

        URI uri;
        try {
            uri = new URI(base);
        } catch (URISyntaxException e) {
            throw invalid("주소 형식이 아닙니다");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw invalid("https 여야 합니다");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) throw invalid("호스트가 없습니다");
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.equals("localhost") || host.endsWith(".localhost")
                || host.startsWith("127.") || host.equals("::1") || host.equals("0.0.0.0")) {
            throw invalid("localhost · 루프백 주소는 쓸 수 없습니다");
        }

        // ★ 경로가 붙어 있으면 주소가 두 번 들어간다 — 실제로 한 번 겪은 함정이다.
        //   LocalAssetStore.url() 이 "/api/public/assets/" 를 직접 붙인다.
        String path = uri.getPath();
        if (path != null && !path.isBlank() && !path.equals("/")) {
            throw invalid("경로 없이 주소만 적습니다 (예: https://example.com)");
        }
    }

    /** ★ 설정값 자체는 메시지에 넣지 않는다 — 로그에 서비스 주소가 그대로 찍힌다 */
    private static IllegalStateException invalid(String why) {
        return new IllegalStateException(
                "prod 의 ASSET_PUBLIC_BASE_URL 이 잘못됐습니다 — " + why + "."
                + " 이 값은 올린 이미지 주소로 DB 에 저장되고 게시된 페이지에 박히므로,"
                + " 틀린 채로 뜨면 그동안 올린 이미지를 되살릴 수 없습니다.");
    }
}
