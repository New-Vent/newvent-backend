package com.newvent.asset.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.newvent.asset.config.AssetProps;
import com.newvent.asset.domain.EventAsset;
import com.newvent.asset.dto.AssetResponse;
import com.newvent.asset.exception.AssetErrorCode;
import com.newvent.asset.exception.AssetException;
import com.newvent.asset.repository.EventAssetRepository;
import com.newvent.registry.Asset;

/**
 * 올리기 · 목록 · 지우기, 그리고 **정화기에 줄 허용 목록**.
 *
 * ★ 확장자와 Content-Type 은 안 믿는다. ImageProbe 가 디코딩해서 알아낸 형식만 쓴다.
 * ★ 덮개 농도도 여기서 한 번 재서 박아 둔다 — 그릴 때마다 잴 수는 없다.
 */
@Service
public class AssetService implements AssetUrls, AssetCleanup {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    /** 디코딩 결과로만 받는 형식. svg 는 없다 — 정화 정책이 따로 필요하다 */
    private static final Set<String> ALLOWED = Set.of("png", "jpeg", "jpg", "gif", "webp");

    private final EventAssetRepository assets;
    private final AssetStore store;
    private final AssetProps props;

    public AssetService(EventAssetRepository assets, AssetStore store, AssetProps props) {
        this.assets = assets;
        this.store = store;
        this.props = props;
    }

    @Transactional
    public AssetResponse upload(long eventId, MultipartFile file, String caption) {
        if (file == null || file.isEmpty()) throw new AssetException(AssetErrorCode.EMPTY_FILE);
        if (file.getSize() > props.maxBytes()) throw new AssetException(AssetErrorCode.TOO_LARGE);
        if (assets.countByEventId(eventId) >= props.maxPerEvent()) {
            throw new AssetException(AssetErrorCode.TOO_MANY);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AssetException(AssetErrorCode.EMPTY_FILE);
        }

        ImageProbe.Probed probed = ImageProbe.probe(bytes)
                .filter(p -> ALLOWED.contains(p.format()))
                .orElseThrow(() -> new AssetException(AssetErrorCode.NOT_AN_IMAGE));

        String ext = "jpeg".equals(probed.format()) ? "jpg" : probed.format();
        String id = UUID.randomUUID().toString().replace("-", "");
        AssetStore.Stored stored = store.put(eventId, id, ext, bytes);

        // ★★ 파일을 먼저 쓰고 행을 넣는다. 그 사이에 터지면 트랜잭션은 되돌아가지만
        //   **파일은 디스크에 남는다** — 아무도 가리키지 않는 고아가 된다.
        //   실제로 그랬다: DB 가 내려간 사이 업로드가 500 이 나고 .assets/{id}.jpg 만 남았다.
        //   박스 디스크가 유한하니 쌓이게 두면 안 된다.
        try {
            EventAsset saved = assets.save(new EventAsset(
                    id, eventId, ext, "image/" + probed.format(),
                    stored.bytes(), stored.url(), probed.scrim(), probed.tone(), trim(caption)));
            return AssetResponse.from(saved);
        } catch (RuntimeException e) {
            // ★ 지우다 또 터져도 **원래 예외를 살린다.** 관리자가 들어야 할 건
            //   "청소에 실패했다" 가 아니라 "왜 업로드가 안 됐나" 다
            try {
                store.delete(eventId, id, ext);
            } catch (RuntimeException ignored) {
                log.warn("고아 파일을 지우지 못했습니다: event={} asset={}.{}", eventId, id, ext);
            }
            throw e;
        }
    }

    /**
     * 이벤트가 영구 삭제될 때 이 이벤트의 이미지를 통째로 치운다.
     *
     * ★ 행은 on delete cascade 로 같이 사라지지만 **파일은 안 사라진다.**
     *   그래서 파일을 지우는 게 이 메서드의 일이다. 행도 같이 지워 둔다 —
     *   cascade 에만 기대면 호출 순서에 따라 남는 경우가 생긴다.
     */
    @Override
    @Transactional
    public void deleteAllOf(long eventId) {
        // ★ 파일 지우기가 실패해도 **이벤트 삭제를 막지 않는다.**
        //   디스크가 잠깐 말썽이라고 관리자가 이벤트를 못 지우면 안 된다.
        //   남은 파일은 아무도 가리키지 않으니 나중에 치우면 된다.
        try {
            store.deleteAll(eventId);
        } catch (RuntimeException e) {
            log.warn("이벤트 {} 의 이미지 파일을 지우지 못했습니다 — 고아 파일이 남습니다", eventId, e);
        }
        assets.deleteAll(assets.findByEventIdOrderByCreatedAtAsc(eventId));
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> list(long eventId) {
        return assets.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(AssetResponse::from).toList();
    }

    /**
     * ★★ 정화기가 쓰는 허용 목록이다. 모델이 쓴 src 가 여기 없으면 &lt;img&gt; 를 통째로 지운다.
     *   이게 없으면 모델이 지어낸 바깥 주소가 그대로 게시된다 — 추적 픽셀이든 뭐든.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Asset.Ref> assetsOf(long eventId) {
        return assets.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(a -> new Asset.Ref(a.getUrl(), a.getCaption(), a.getScrim(), a.getTone()))
                .toList();
    }


    @Transactional(readOnly = true)
    public Optional<EventAsset> find(String assetId) {
        return assets.findById(assetId);
    }

    public Optional<InputStream> open(EventAsset a) {
        return store.open(a.getEventId(), a.getId(), a.getExtension());
    }

    /**
     * ★ 지워도 이미 저장된 버전의 HTML 에는 주소가 남는다. 그 페이지는 깨진 이미지가 된다.
     *   되돌리기(과거 버전)를 생각하면 파일을 지우지 않는 편이 안전하지만,
     *   박스 디스크가 유한해서 지운다. 화면에서 지울 때 그 점을 알려 줘야 한다.
     */
    @Transactional
    public void delete(long eventId, String assetId) {
        EventAsset a = assets.findByIdAndEventId(assetId, eventId)
                .orElseThrow(() -> new AssetException(AssetErrorCode.NOT_FOUND));
        store.delete(eventId, a.getId(), a.getExtension());
        assets.delete(a);
    }

    private static String trim(String caption) {
        if (caption == null) return null;
        String t = caption.strip();
        if (t.isEmpty()) return null;
        return t.length() > 120 ? t.substring(0, 120) : t;
    }
}
