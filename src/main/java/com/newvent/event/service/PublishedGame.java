package com.newvent.event.service;

import java.util.Map;
import java.util.Optional;

/**
 * 게시 페이지에 그릴 참여 방식. <b>게임 UI 를 그릴지, 어떤 모양으로 그릴지가 이걸로 갈린다.</b>
 *
 * ★ 왜 인터페이스인가 — 패키지 순환을 만들지 않으려고
 *   참여 설정(event_game_configs)과 그 검증기는 participation 패키지에 있는데,
 *   participation 은 이미 event 를 본다 (ParticipationStatusService → PublicEventService).
 *   PublicEventService 가 거기를 직접 꺼내면 두 패키지가 서로를 보게 된다.
 *   그래서 <b>필요한 쪽(event)이 모양을 정하고, 아는 쪽(participation)이 구현한다.</b>
 *
 * ★ 왜 Map 으로 받나 — registry 가 participation 을 몰라야 한다
 *   실제로 마크업을 그리는 {@code registry.GameWidget} 은 방식 코드와 설정 값만 있으면 된다.
 *   EventGameConfig 엔티티를 거기까지 들고 가면 레지스트리가 DB 모델에 묶인다.
 */
public interface PublishedGame {

    /**
     * 이 이벤트가 참여를 받는 방식. <b>참여 API 와 같은 기준</b>으로 본다 —
     * 설정이 하나가 아니거나 내용이 틀리면 빈 값이다.
     *
     * ★ 빈 값이면 게시 페이지에 게임 UI 를 그리지 않고 {@code <body data-game>} 도 적지 않는다.
     *   그러면 런타임이 「참여 기능은 준비 중이에요」로 안내한다.
     */
    Optional<Spec> of(Long eventId);

    /**
     * @param gameCode BASIC · SPORTS_PREDICTION · PRE_REGISTRATION · LUCKY_POUCH
     * @param config   그 방식의 설정 (event_game_configs.config)
     */
    record Spec(String gameCode, Map<String, Object> config) { }
}
