package com.newvent.asset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.asset.config.AssetProps;
import com.newvent.asset.config.ProdAssetGuard;

/**
 * prod 에서 이미지 주소가 잘못돼 있으면 기동을 막는다.
 *
 * ★★ 왜 이게 기동을 막을 만한 일인가 — <b>주소가 DB 에 박힌다.</b>
 *   업로드할 때 완성한 주소가 event_assets.url 에 저장되고 게시된 페이지의 &lt;img src&gt;
 *   에도 그대로 들어간다. 틀린 채로 떠서 몇 장 올리고 게시까지 하면,
 *   나중에 환경변수를 고쳐도 그 행들과 그 페이지들은 안 고쳐진다.
 */
class ProdAssetGuardTest {

    private static AssetProps props(String publicBaseUrl) {
        return new AssetProps("local", "/data/assets", publicBaseUrl, 3145728L, 20);
    }

    private static void rejects(String publicBaseUrl) {
        assertThrows(IllegalStateException.class, () -> new ProdAssetGuard(props(publicBaseUrl)),
                publicBaseUrl + " 는 막아야 합니다");
    }

    // ★ 제일 위험한 값이다. 아무것도 안 넣으면 이게 들어간다
    @Test
    @DisplayName("★ 기본값(localhost)으로는 뜨지 못한다")
    void 기본값() {
        rejects("http://localhost:8080");
        rejects("https://127.0.0.1:8080");
    }

    // ★ 서비스가 https 라 브라우저가 혼합 콘텐츠로 이미지만 차단한다.
    //   페이지는 멀쩡히 뜨고 사진만 안 보여서 원인을 찾기 어렵다
    @Test
    @DisplayName("http 는 막는다 - 혼합 콘텐츠로 이미지만 차단된다")
    void http는_막는다() {
        rejects("http://newvent.duckdns.org");
    }

    // ★ 실제로 한 번 겪은 함정이다. LocalAssetStore 가 경로를 직접 붙인다
    @Test
    @DisplayName("경로가 붙어 있으면 막는다 - 주소가 두 번 들어간다")
    void 경로가_붙으면() {
        rejects("https://newvent.duckdns.org/api/public/assets");
    }

    @Test
    @DisplayName("비어 있거나 안 풀린 환경변수도 막는다")
    void 빈_값() {
        rejects("");
        rejects("   ");
        rejects("${ASSET_PUBLIC_BASE_URL}");
    }

    @Test
    @DisplayName("서비스 주소는 통과한다 - 끝 슬래시는 봐준다")
    void 통과() {
        assertDoesNotThrow(() -> new ProdAssetGuard(props("https://newvent.duckdns.org")));
        assertDoesNotThrow(() -> new ProdAssetGuard(props("https://newvent.duckdns.org/")));
    }
}
