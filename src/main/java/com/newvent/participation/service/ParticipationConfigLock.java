package com.newvent.participation.service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 참여 기록이 있는 이벤트의 설정을 <b>어디까지</b> 바꿀 수 있나.
 *
 * ★★ 왜 생겼나 — 전에는 참여 한 건으로 설정이 영구히 잠겼다
 *   {@code existsByEventId} 가 참이면 저장을 통째로 거부했다. 근거는 맞다 —
 *   이미 저장된 {@code result_data} 는 그때의 방식을 전제하므로, 방식을 바꾸면
 *   그 기록을 설명할 수 없게 된다. 그런데 <b>너무 넓게 잠갔다.</b>
 *
 *     · 경품명에 오타가 나면 영원히 못 고친다 ("데어터 5GB")
 *     · <b>스포츠 예측형은 아예 성립하지 않는다</b> — 정답은 경기가 끝난 뒤에야 아는데,
 *       참여자가 한 명이라도 들어오면 정답을 넣을 길이 없다
 *
 * ★ 그래서 기준을 바꾼다 — 「바꾸면 <b>이미 결정된 결과</b>가 거짓이 되는가」
 *
 *     바꿀 수 있다   아직 결정되지 않은 것만 건드린다
 *     바꿀 수 없다   저장된 result_data 의 의미나 그 공정성을 뒤집는다
 */
final class ParticipationConfigLock {

    private ParticipationConfigLock() {}

    /**
     * 참여 기록이 있어도 바꿀 수 있는 설정 값.
     *
     *   prizeName       경품명. 저장된 당첨 기록은 그때의 이름을 <b>자기 안에</b> 들고 있다
     *                   (result_data.prizeName). 설정을 고쳐도 과거 기록은 바뀌지 않는다.
     *                   앞으로의 당첨에만 적용된다 — 오타 수정이 이 길이다.
     *
     *   announcementAt  발표 시각. 그때까지의 참여는 전부 PENDING 이라 결정된 게 없다.
     *                   경기·행사가 밀리면 옮길 수 있어야 한다.
     *
     * ★ 스포츠 예측형의 정답(correctAnswer)은 <b>일부러 넣지 않았다.</b>
     *   그 채점 기능이 아직 없다 — 지금 허용하면 아무도 쓰지 않는 구멍이 된다.
     *   채점을 만드는 쪽에서 여기에 "correctAnswer" 를 더하면 된다.
     *   근거는 위와 같다: 정답을 넣기 전의 참여는 전부 PENDING 이라 결정된 게 없다.
     *
     * ★ 여기 없는 것은 전부 잠긴다. 특히:
     *     gameCode           방식이 바뀌면 저장된 submitted_data 가 의미를 잃는다
     *                        (BASIC 기록에는 pouchIndex 가 없다)
     *     resultMode         IMMEDIATE 로 이미 갈린 WON·LOST 가 설명되지 않는다
     *     winProbability     참여자가 보고 참여한 확률이다. 중간에 바꾸면 공정성이 깨진다
     *     winnerCount        같은 이유 — "2명 추첨" 을 보고 참여했다
     *     predictionOptions  선택지를 지우면 그걸 고른 참여 기록이 떠버린다
     *     pouches            같은 이유
     */
    private static final Set<String> CHANGEABLE_AFTER_PARTICIPATION =
            Set.of("prizeName", "announcementAt");

    /**
     * 참여 기록이 있는 상태에서 이 변경이 허용되나.
     *
     * ★ 방식(gameCode)이 바뀌면 값을 비교할 필요도 없다 — 무조건 거부다.
     * ★ 값이 실제로 달라진 키만 본다. 프론트가 설정 전체를 그대로 PUT 해도
     *   (설정 화면은 보통 폼 전체를 보낸다) 바뀐 게 없으면 통과해야 한다.
     *
     * @param before 저장돼 있던 설정
     * @param after  이번에 저장하려는 설정
     */
    static boolean allows(String beforeGameCode, String afterGameCode,
                          Map<String, Object> before, Map<String, Object> after) {
        if (!Objects.equals(beforeGameCode, afterGameCode)) {
            return false;
        }
        return changedKeys(before, after).stream()
                .allMatch(CHANGEABLE_AFTER_PARTICIPATION::contains);
    }

    /**
     * 값이 달라진 키. <b>사라진 키도 변경이다</b> — 경품명을 지우는 것도 변경이다.
     *
     * ★ Objects.equals 로 비교한다. JSONB 에서 올라온 값은 Integer·String·List·Map 이고
     *   전부 equals 가 값 비교다. 숫자 타입이 갈리는 경우(30 vs 30.0)는 변경으로 잡히는데,
     *   그쪽이 안전한 방향이다 — 놓치는 것보다 한 번 더 막는 게 낫다.
     */
    private static Set<String> changedKeys(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> left = before == null ? Map.of() : before;
        Map<String, Object> right = after == null ? Map.of() : after;

        return java.util.stream.Stream.concat(left.keySet().stream(), right.keySet().stream())
                .filter(key -> !Objects.equals(left.get(key), right.get(key)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
