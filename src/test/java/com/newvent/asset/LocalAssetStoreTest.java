package com.newvent.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.newvent.asset.service.AssetStore;
import com.newvent.asset.service.LocalAssetStore;

/**
 * 디스크 저장소 — 스프링 없이 돈다.
 *
 * ★ 여기서 제일 중요한 건 **경로 탈출**이다. assetId 와 확장자가 바깥에서 와서
 *   파일 이름이 되므로, 모양 검사가 없으면 "../../etc/passwd" 를 쓴다.
 */
class LocalAssetStoreTest {

    private static final String ID = "0123456789abcdef0123456789abcdef";
    private static final byte[] BYTES = { 1, 2, 3, 4, 5 };

    private LocalAssetStore store(Path root) {
        return new LocalAssetStore(root, "https://newvent.example/");
    }

    @Test
    @DisplayName("저장하면 절대 URL 을 돌려준다 - 끝의 / 는 겹치지 않는다")
    void 저장하면_절대_URL(@TempDir Path root) {
        AssetStore.Stored s = store(root).put(7, ID, "png", BYTES);

        assertEquals("https://newvent.example/api/public/assets/" + ID + ".png", s.url());
        assertEquals(BYTES.length, s.bytes());
    }

    @Test
    @DisplayName("이벤트별로 디렉터리가 갈린다 - 다른 이벤트 id 로는 안 열린다")
    void 이벤트별_디렉터리(@TempDir Path root) {
        LocalAssetStore st = store(root);
        st.put(7, ID, "png", BYTES);

        assertTrue(st.open(7, ID, "png").isPresent());
        assertFalse(st.open(8, ID, "png").isPresent());
    }

    // ★ 쓰다 만 파일이 내려가면 깨진 이미지가 게시된다. 옆에 쓰고 옮긴다.
    @Test
    @DisplayName("임시 파일(.part)을 남기지 않는다")
    void 임시파일을_안_남긴다(@TempDir Path root) throws IOException {
        store(root).put(7, ID, "png", BYTES);

        try (Stream<Path> walk = Files.walk(root)) {
            assertFalse(walk.anyMatch(p -> p.toString().endsWith(".part")));
        }
    }

    @Test
    @DisplayName("지우면 안 열린다. 없는 것을 지워도 터지지 않는다")
    void 지우기(@TempDir Path root) {
        LocalAssetStore st = store(root);
        st.put(7, ID, "png", BYTES);

        st.delete(7, ID, "png");
        assertFalse(st.open(7, ID, "png").isPresent());

        st.delete(7, ID, "png");   // 두 번째도 조용히 지나간다
    }

    // ★★ 이게 이 클래스의 핵심이다. 모양 검사가 없으면 파일 시스템 어디든 쓸 수 있다.
    @Test
    @DisplayName("★ assetId 모양이 아니면 거절한다 - 경로 탈출")
    void 경로_탈출을_막는다(@TempDir Path root) {
        LocalAssetStore st = store(root);

        for (String bad : new String[] { "../../etc/passwd", "..", "", "abc",
                "0123456789abcdef0123456789abcde", "0123456789ABCDEF0123456789abcdef" }) {
            assertThrows(IllegalArgumentException.class, () -> st.put(7, bad, "png", BYTES), bad);
        }
    }

    @Test
    @DisplayName("★ 확장자 모양이 아니면 거절한다")
    void 확장자_탈출을_막는다(@TempDir Path root) {
        LocalAssetStore st = store(root);

        for (String bad : new String[] { "../x", "p", "pngpng", "PNG", "" }) {
            assertThrows(IllegalArgumentException.class, () -> st.put(7, ID, bad, BYTES), bad);
        }
    }
}
