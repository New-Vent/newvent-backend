package com.newvent.asset.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 박스 디스크에 둔다 — 도커 볼륨 `newvent-assets` 가 `/data/assets` 로 붙는다.
 *
 * ★ 볼륨 이름이 걸린 함정은 docker-compose.app.yml 주석과 같다.
 *   `/opt/newvent` 에서 `-p` 없이 올려야 `newvent_newvent-assets` 가 유지된다.
 *   다른 디렉터리에서 올리면 **빈 볼륨이 새로 생겨서 이미지가 전부 사라진 것처럼 보인다.**
 *
 * ★ 이벤트별로 디렉터리를 나눈다. 이벤트를 지울 때 통째로 지울 수 있고,
 *   한 디렉터리에 파일이 수만 개 쌓이지 않는다.
 */
public class LocalAssetStore implements AssetStore {

    private final Path root;
    private final String publicBaseUrl;

    public LocalAssetStore(Path root, String publicBaseUrl) {
        this.root = root;
        // 끝의 / 는 떼 둔다 — 안 그러면 주소에 // 가 생기고, 그게 그대로 허용 목록에 들어간다
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Override
    public Stored put(long eventId, String assetId, String extension, byte[] bytes) {
        Path file = pathOf(eventId, assetId, extension);
        try {
            Files.createDirectories(file.getParent());
            // ★ 같은 자리에 먼저 쓰고 옮긴다. 쓰다 만 파일이 내려가는 일을 막는다
            Path tmp = file.resolveSibling(file.getFileName() + ".part");
            Files.write(tmp, bytes);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("에셋을 저장하지 못했습니다: " + file, e);
        }
        return new Stored(url(assetId, extension), bytes.length);
    }

    @Override
    public Optional<InputStream> open(long eventId, String assetId, String extension) {
        Path file = pathOf(eventId, assetId, extension);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(Files.newInputStream(file));
        } catch (IOException e) {
            throw new UncheckedIOException("에셋을 열지 못했습니다: " + file, e);
        }
    }

    @Override
    public void delete(long eventId, String assetId, String extension) {
        try {
            Files.deleteIfExists(pathOf(eventId, assetId, extension));
        } catch (IOException e) {
            throw new UncheckedIOException("에셋을 지우지 못했습니다: " + assetId, e);
        }
    }

    /** 공개 주소 — 게시 페이지(/e/*)는 비로그인이라 /api/admin 아래면 안 된다 */
    public String url(String assetId, String extension) {
        return publicBaseUrl + "/api/public/assets/" + assetId + "." + extension;
    }

    /**
     * ★ assetId 와 확장자를 **파일 이름으로 쓰기 전에** 모양을 검사한다.
     *   바깥에서 온 값이 경로에 그대로 들어가면 "../../etc/passwd" 가 된다.
     */
    private Path pathOf(long eventId, String assetId, String extension) {
        if (!assetId.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("assetId 모양이 아닙니다: " + assetId);
        }
        if (!extension.matches("[a-z0-9]{3,4}")) {
            throw new IllegalArgumentException("확장자 모양이 아닙니다: " + extension);
        }
        return root.resolve(String.valueOf(eventId)).resolve(assetId + "." + extension);
    }
}
