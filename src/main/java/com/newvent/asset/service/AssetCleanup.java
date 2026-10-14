package com.newvent.asset.service;

/**
 * "이 이벤트의 이미지를 전부 치워라" — 이벤트를 영구 삭제할 때 쓰는 한 가지.
 *
 * ★ 왜 인터페이스인가 — {@link AssetUrls} 와 같은 이유다.
 *   EventService 가 asset 모듈 전체(저장소 · 트랜잭션)를 끌고 오지 않게 한다.
 *   테스트는 이 인터페이스를 mock 한다.
 *
 * ★★ DB 행만으로는 모자란다.
 *   event_assets 는 on delete cascade 라 이벤트가 지워지면 행은 같이 사라진다.
 *   그런데 **디스크의 파일은 그대로 남는다.** 아무도 가리키지 않는 파일이
 *   박스에 영원히 쌓인다 — 한 이벤트에 3MB × 20장이면 60MB 다.
 */
public interface AssetCleanup {

    void deleteAllOf(long eventId);
}
