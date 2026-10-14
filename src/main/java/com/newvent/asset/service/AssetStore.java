package com.newvent.asset.service;

import java.io.InputStream;
import java.util.Optional;

/**
 * 올린 이미지를 어디에 두는가 — 구현을 갈아끼울 수 있게 뽑아낸 지점.
 *
 * ★ 지금은 {@link LocalAssetStore} 하나뿐이다. EC2 박스의 도커 볼륨에 둔다.
 *   박스가 1.9GB 라 DB(bytea)는 뺐고, S3 는 버킷 · IAM 까지 손봐야 해서 뒤로 미뤘다.
 *   옮길 때 고칠 곳은 이 인터페이스의 구현 하나와 설정(asset.store)뿐이다 —
 *   LlmClient 가 mock · ollama · bedrock 을 고르는 것과 같은 모양이다.
 *
 * ★ 주소는 **절대 URL** 로 돌려준다. 정화기(BlockValidator)가 상대 경로를 못 받는다 —
 *   jsoup 이 img[src] 를 absUrl 로 바꾼 뒤 프로토콜을 보는데 baseUri 가 비어 있다.
 */
public interface AssetStore {

    /** 저장하고 주소를 돌려준다. assetId 는 추측할 수 없는 값이어야 한다 — 공개 경로다 */
    Stored put(long eventId, String assetId, String extension, byte[] bytes);

    /** 내려 주기 위해 연다. 없으면 빈 값 */
    Optional<InputStream> open(long eventId, String assetId, String extension);

    /** 지운다. 없으면 아무 일도 하지 않는다 */
    void delete(long eventId, String assetId, String extension);

    /**
     * 이 이벤트의 이미지를 통째로 지운다 — 이벤트가 영구 삭제될 때 쓴다.
     *
     * ★ DB 행은 event_assets 의 on delete cascade 로 같이 사라지지만
     *   **디스크의 파일은 안 사라진다.** 그러면 아무도 가리키지 않는 파일이 박스에 쌓인다.
     * ★ 없어도 아무 일도 하지 않는다. 이벤트에 이미지가 하나도 없는 경우가 흔하다.
     */
    void deleteAll(long eventId);

    record Stored(String url, long bytes) {}
}
