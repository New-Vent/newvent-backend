package com.newvent.asset.service;

import java.util.List;

import com.newvent.registry.Asset;

/**
 * "이 이벤트에 올라온 이미지가 무엇인가" — 수정 경로가 묻는 한 가지.
 *
 * ★ 왜 인터페이스인가 — EditService 가 asset 모듈 전체(저장소 · 트랜잭션)를 끌고 오지 않게 한다.
 *   EventGuard · VersionStore 와 같은 모양이다. 테스트는 NONE 을 쓴다.
 *
 * ★ 두 군데에서 쓴다.
 *   프롬프트  어떤 이미지를 쓸 수 있는지 모델에게 알려 준다 (주소 + 설명)
 *   정화기    모델이 쓴 src 가 여기 없으면 &lt;img&gt; 를 통째로 지운다
 *   그래서 목록이 **하나의 진실**이어야 한다. 두 군데가 다른 목록을 보면 조용히 사라진다.
 */
public interface AssetUrls {

    List<Asset.Ref> assetsOf(long eventId);

    /** 올린 이미지를 쓰지 않는 구성 — 테스트 · 목업. 이미지가 하나도 없다고 답한다 */
    AssetUrls NONE = eventId -> List.of();
}
