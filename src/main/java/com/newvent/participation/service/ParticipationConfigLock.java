package com.newvent.participation.service;

import java.util.List;
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
     *     pouches · rewards  같은 이유 — 단, <b>그 안의 경품명만</b> 바꾸는 것은 통과한다
     *                        ({@link #NESTED_PRIZE_NAME} 참고)
     *
     * ★ 이 집합은 <b>최상위 키</b>만 본다. 중첩된 경품명은 아래 표가 따로 다룬다.
     */
    private static final Set<String> CHANGEABLE_AFTER_PARTICIPATION =
            Set.of("prizeName", "announcementAt");

    /**
     * 경품명이 <b>배열 안에 중첩된</b> 방식 — 키 → 그 안에서 칸을 가리키는 신분 필드.
     *
     * ★★ 왜 필요한가 — 최상위 키만 보면 이 방식들은 경품명을 영영 못 고친다
     *   복주머니:    {"pouches":[{"pouchIndex":1,"winProbability":30,"prizeName":"…"}, …]}
     *   승부 예측형: {"rewards":[{"rewardId":"r1","target":"…","selection":"DRAW",
     *                            "winnerCount":2,"prizeName":"…"}, …]}
     *   경품명 한 글자만 고쳐도 「바뀐 키 = pouches」·「= rewards」 로 잡혀 거부됐다.
     *   이 잠금을 좁힌 목적 자체가 오타 수정인데, 그 두 방식만 못 누리는 상태였다.
     *
     * ★ 그렇다고 키를 허용 목록에 넣으면 안 된다. <b>같은 칸에 잠겨야 할 값이 있다</b> —
     *   복주머니의 winProbability, 승부 예측형의 target · selection · winnerCount 다.
     *   전부 참여자가 보고 참여한 조건이라 바뀌면 공정성이 깨진다.
     *   그래서 칸 구성이 그대로이고 <b>경품명만</b> 다를 때에만 통과시킨다.
     *
     * ★ 칸은 배열 순서가 아니라 신분 필드로 짝짓는다. 순서로 비교하면 프론트가 정렬만
     *   바꿔 보내도 거부되는데, 그건 지금 고치는 버그와 같은 종류다.
     *
     * ★ 방식을 더할 때 경품명이 중첩되어 있으면 여기 한 줄을 더하면 된다.
     */
    private static final Map<String, String> NESTED_PRIZE_NAME = Map.of(
            "pouches", "pouchIndex",
            "rewards", "rewardId");

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

        Map<String, Object> left = before == null ? Map.of() : before;
        Map<String, Object> right = after == null ? Map.of() : after;

        return changedKeys(left, right).stream()
                .allMatch(key -> CHANGEABLE_AFTER_PARTICIPATION.contains(key)
                        || prizeNameOnly(key, left.get(key), right.get(key)));
    }

    /**
     * 중첩된 배열에서 <b>경품명만</b> 달라졌나. 근거는 {@link #NESTED_PRIZE_NAME} 에 있다.
     *
     * ★ 모양이 조금이라도 수상하면 false 다 — 칸 수가 다르거나, 신분 집합이 다르거나,
     *   Map·List 가 아니거나. 판단이 안 되는 것은 거부가 안전한 방향이다.
     */
    private static boolean prizeNameOnly(String key, Object before, Object after) {
        String idField = NESTED_PRIZE_NAME.get(key);

        if (idField == null
                || !(before instanceof List<?> left) || !(after instanceof List<?> right)
                || left.size() != right.size()) {
            return false;
        }

        Map<Object, Map<?, ?>> leftById = byId(left, idField);
        Map<Object, Map<?, ?>> rightById = byId(right, idField);

        if (leftById == null || rightById == null
                || !leftById.keySet().equals(rightById.keySet())) {
            return false;
        }

        return leftById.keySet().stream().allMatch(id ->
                changedKeysOf(leftById.get(id), rightById.get(id))
                        .stream().allMatch("prizeName"::equals));
    }

    /** 신분 필드 → 칸. 값이 없거나 중복이면 null — 짝지을 수 없으면 비교하지 않는다 */
    private static Map<Object, Map<?, ?>> byId(List<?> items, String idField) {
        Map<Object, Map<?, ?>> out = new java.util.LinkedHashMap<>();

        for (Object item : items) {
            if (!(item instanceof Map<?, ?> entry)) {
                return null;
            }
            Object id = entry.get(idField);
            if (id == null || out.put(id, entry) != null) {
                return null;
            }
        }
        return out;
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

        return changedKeysOf(left, right).stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** 키 타입을 가리지 않는 판본. 복주머니 칸(Map&lt;?,?&gt;)끼리 비교할 때 쓴다 */
    private static Set<Object> changedKeysOf(Map<?, ?> left, Map<?, ?> right) {
        return java.util.stream.Stream.concat(left.keySet().stream(), right.keySet().stream())
                .filter(key -> !Objects.equals(left.get(key), right.get(key)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
