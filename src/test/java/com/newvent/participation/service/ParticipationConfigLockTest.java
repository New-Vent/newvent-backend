package com.newvent.participation.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 참여 기록이 있을 때 설정을 어디까지 바꿀 수 있나.
 *
 * ★ 기준은 하나다 — 「바꾸면 <b>이미 결정된 결과</b>가 거짓이 되는가」
 */
class ParticipationConfigLockTest {

    private static final Map<String, Object> IMMEDIATE = Map.of(
            "resultMode", "IMMEDIATE",
            "winProbability", 30,
            "prizeName", "데이터 5GB 쿠폰");

    // ── 바꿀 수 있다 ──────────────────────────────────────────────

    @Test
    @DisplayName("경품명은 바꿀 수 있다 — 오타를 영원히 못 고치는 게 전의 문제였다")
    void 경품명은_바꿀수있다() {
        assertTrue(allows(IMMEDIATE, with(IMMEDIATE, "prizeName", "데이터 10GB 쿠폰")));
    }

    @Test
    @DisplayName("발표 시각은 바꿀 수 있다 — 경기가 밀리면 옮겨야 한다")
    void 발표시각은_바꿀수있다() {
        Map<String, Object> before = Map.of(
                "resultMode", "DELAYED", "winnerCount", 2,
                "prizeName", "쿠폰", "announcementAt", "2026-12-01T20:00:00+09:00");

        assertTrue(allows(before, with(before, "announcementAt", "2026-12-08T20:00:00+09:00")));
    }

    @Test
    @DisplayName("바뀐 게 없으면 통과한다 — 설정 화면은 폼 전체를 그대로 PUT 한다")
    void 변경이없으면_통과한다() {
        assertTrue(allows(IMMEDIATE, new HashMap<>(IMMEDIATE)));
    }

    @Test
    @DisplayName("허용된 값을 여러 개 같이 바꿔도 된다")
    void 허용값_여러개도_된다() {
        Map<String, Object> before = Map.of(
                "resultMode", "DELAYED", "winnerCount", 2,
                "prizeName", "굳즈", "announcementAt", "2026-12-01T20:00:00+09:00");
        Map<String, Object> after = new HashMap<>(before);
        after.put("prizeName", "응원 굳즈 세트");
        after.put("announcementAt", "2026-12-02T20:00:00+09:00");

        assertTrue(allows(before, after));
    }

    @Test
    @DisplayName("예측형 정답은 아직 허용하지 않는다 — 채점 기능이 없다. 그걸 만드는 쪽에서 열면 된다")
    void 정답은_아직_거부한다() {
        Map<String, Object> before = Map.of(
                "predictionOptions", List.of("HOME_WIN", "DRAW", "AWAY_WIN"),
                "announcementAt", "2026-12-01T20:00:00+09:00",
                "prizeName", "응원 굳즈");

        assertFalse(allows("SPORTS_PREDICTION", before, with(before, "correctAnswer", "HOME_WIN")));
    }

    // ── 바꿀 수 없다 ──────────────────────────────────────────────

    @Test
    @DisplayName("방식을 바꾸면 거부한다 — BASIC 기록에는 pouchIndex 가 없다")
    void 방식변경은_거부한다() {
        assertFalse(ParticipationConfigLock.allows(
                "BASIC", "LUCKY_POUCH", IMMEDIATE, IMMEDIATE));
    }

    @Test
    @DisplayName("resultMode 를 바꾸면 거부한다 — 이미 갈린 WON·LOST 가 설명되지 않는다")
    void 결과방식_변경은_거부한다() {
        assertFalse(allows(IMMEDIATE, with(IMMEDIATE, "resultMode", "GUARANTEED")));
    }

    @Test
    @DisplayName("당첨 확률을 바꾸면 거부한다 — 참여자가 보고 참여한 확률이다")
    void 확률변경은_거부한다() {
        assertFalse(allows(IMMEDIATE, with(IMMEDIATE, "winProbability", 90)));
    }

    @Test
    @DisplayName("당첨 인원을 바꾸면 거부한다 — \"2명 추첨\" 을 보고 참여했다")
    void 당첨인원_변경은_거부한다() {
        Map<String, Object> before = Map.of(
                "resultMode", "DELAYED", "winnerCount", 2,
                "prizeName", "쿠폰", "announcementAt", "2026-12-01T20:00:00+09:00");

        assertFalse(allows(before, with(before, "winnerCount", 100)));
    }

    @Test
    @DisplayName("선택지를 바꾸면 거부한다 — 지운 선택지를 고른 참여 기록이 떠버린다")
    void 선택지_변경은_거부한다() {
        Map<String, Object> before = Map.of(
                "predictionOptions", List.of("HOME_WIN", "DRAW", "AWAY_WIN"),
                "announcementAt", "2026-12-01T20:00:00+09:00",
                "prizeName", "굳즈");

        assertFalse(allows("SPORTS_PREDICTION", before,
                with(before, "predictionOptions", List.of("HOME_WIN"))));
    }

    // ── 복주머니 — 경품명이 칸 안에 중첩되어 있다 ──────────────────

    /** 칸 둘. 1번 경품명에 오타가 있다 */
    private static Map<String, Object> pouches(String firstPrize, int firstProbability) {
        return Map.of("pouches", List.of(
                Map.of("pouchIndex", 1, "winProbability", firstProbability, "prizeName", firstPrize),
                Map.of("pouchIndex", 2, "winProbability", 10, "prizeName", "커피 쿠폰")));
    }

    @Test
    @DisplayName("★ 복주머니도 경품명은 고칠 수 있다 — 최상위 키만 보면 영영 못 고쳤다")
    void 복주머니_경품명은_바꿀수있다() {
        assertTrue(allows("LUCKY_POUCH",
                pouches("데어터 5GB", 30),
                pouches("데이터 5GB", 30)));
    }

    @Test
    @DisplayName("칸 안의 확률을 바꾸면 거부한다 — 경품명과 같은 칸에 있어도 열어 주지 않는다")
    void 복주머니_확률변경은_거부한다() {
        assertFalse(allows("LUCKY_POUCH",
                pouches("데이터 5GB", 30),
                pouches("데이터 5GB", 90)));
    }

    @Test
    @DisplayName("경품명과 확률을 같이 바꾸면 거부한다 — 묶어서 통과시키지 않는다")
    void 복주머니_섞어바꾸면_거부한다() {
        assertFalse(allows("LUCKY_POUCH",
                pouches("데어터 5GB", 30),
                pouches("데이터 5GB", 90)));
    }

    @Test
    @DisplayName("칸을 더하거나 빼면 거부한다")
    void 복주머니_칸수변경은_거부한다() {
        Map<String, Object> added = Map.of("pouches", List.of(
                Map.of("pouchIndex", 1, "winProbability", 30, "prizeName", "데이터 5GB"),
                Map.of("pouchIndex", 2, "winProbability", 10, "prizeName", "커피 쿠폰"),
                Map.of("pouchIndex", 3, "winProbability", 5, "prizeName", "한우세트")));

        assertFalse(allows("LUCKY_POUCH", pouches("데이터 5GB", 30), added));
    }

    @Test
    @DisplayName("칸 순서가 바뀌어도 경품명만 다르면 통과한다 — pouchIndex 로 짝짓는다")
    void 복주머니_순서가바뀌어도_통과한다() {
        Map<String, Object> reordered = Map.of("pouches", List.of(
                Map.of("pouchIndex", 2, "winProbability", 10, "prizeName", "커피 쿠폰"),
                Map.of("pouchIndex", 1, "winProbability", 30, "prizeName", "데이터 5GB")));

        assertTrue(allows("LUCKY_POUCH", pouches("데어터 5GB", 30), reordered));
    }

    @Test
    @DisplayName("칸 번호가 달라지면 거부한다 — 어느 칸과 비교할지 정할 수 없다")
    void 복주머니_번호변경은_거부한다() {
        Map<String, Object> renumbered = Map.of("pouches", List.of(
                Map.of("pouchIndex", 1, "winProbability", 30, "prizeName", "데이터 5GB"),
                Map.of("pouchIndex", 9, "winProbability", 10, "prizeName", "커피 쿠폰")));

        assertFalse(allows("LUCKY_POUCH", pouches("데이터 5GB", 30), renumbered));
    }

    // ── 승부 예측형 — 경품명이 rewards 안에 중첩되어 있다 (#215) ──

    /** 혜택 둘. 전원 지급 + 정답자 중 추첨 */
    private static Map<String, Object> rewards(String drawPrize, int winnerCount, String selection) {
        return Map.of("rewards", List.of(
                Map.of("rewardId", "r1", "target", "ALL_PARTICIPANTS", "selection", "ALL",
                        "winnerCount", 0, "prizeName", "참가 포인트 5,000P"),
                Map.of("rewardId", "r2", "target", "CORRECT_PREDICTIONS", "selection", selection,
                        "winnerCount", winnerCount, "prizeName", drawPrize)));
    }

    @Test
    @DisplayName("★ 승부 예측형도 경품명은 고칠 수 있다 — rewards 안에 중첩되어 거부되던 것")
    void 예측형_경품명은_바꿀수있다() {
        assertTrue(allows("SPORTS_PREDICTION",
                rewards("상푼권", 2, "DRAW"),
                rewards("상품권", 2, "DRAW")));
    }

    @Test
    @DisplayName("추첨 인원을 바꾸면 거부한다 — 참여자가 보고 참여한 조건이다")
    void 예측형_인원변경은_거부한다() {
        assertFalse(allows("SPORTS_PREDICTION",
                rewards("상품권", 2, "DRAW"),
                rewards("상품권", 50, "DRAW")));
    }

    @Test
    @DisplayName("선정 방식을 바꾸면 거부한다 — 추첨이던 것이 전원 지급이 될 수 없다")
    void 예측형_선정방식_변경은_거부한다() {
        assertFalse(allows("SPORTS_PREDICTION",
                rewards("상품권", 2, "DRAW"),
                rewards("상품권", 2, "ALL")));
    }

    @Test
    @DisplayName("경품명과 인원을 같이 바꾸면 거부한다 — 묶어서 통과시키지 않는다")
    void 예측형_섞어바꾸면_거부한다() {
        assertFalse(allows("SPORTS_PREDICTION",
                rewards("상푼권", 2, "DRAW"),
                rewards("상품권", 50, "DRAW")));
    }

    @Test
    @DisplayName("혜택을 더하거나 rewardId 를 바꾸면 거부한다 — 짝지을 수 없다")
    void 예측형_혜택구성_변경은_거부한다() {
        Map<String, Object> added = Map.of("rewards", List.of(
                Map.of("rewardId", "r1", "target", "ALL_PARTICIPANTS", "selection", "ALL",
                        "winnerCount", 0, "prizeName", "참가 포인트 5,000P"),
                Map.of("rewardId", "r2", "target", "CORRECT_PREDICTIONS", "selection", "DRAW",
                        "winnerCount", 2, "prizeName", "상품권"),
                Map.of("rewardId", "r3", "target", "CORRECT_PREDICTIONS", "selection", "DRAW",
                        "winnerCount", 1, "prizeName", "한우세트")));

        assertFalse(allows("SPORTS_PREDICTION", rewards("상품권", 2, "DRAW"), added));

        Map<String, Object> renamed = Map.of("rewards", List.of(
                Map.of("rewardId", "r1", "target", "ALL_PARTICIPANTS", "selection", "ALL",
                        "winnerCount", 0, "prizeName", "참가 포인트 5,000P"),
                Map.of("rewardId", "r9", "target", "CORRECT_PREDICTIONS", "selection", "DRAW",
                        "winnerCount", 2, "prizeName", "상품권")));

        assertFalse(allows("SPORTS_PREDICTION", rewards("상품권", 2, "DRAW"), renamed));
    }

    @Test
    @DisplayName("혜택 순서가 바뀌어도 경품명만 다르면 통과한다 — rewardId 로 짝짓는다")
    void 예측형_순서가바뀌어도_통과한다() {
        Map<String, Object> reordered = Map.of("rewards", List.of(
                Map.of("rewardId", "r2", "target", "CORRECT_PREDICTIONS", "selection", "DRAW",
                        "winnerCount", 2, "prizeName", "상품권"),
                Map.of("rewardId", "r1", "target", "ALL_PARTICIPANTS", "selection", "ALL",
                        "winnerCount", 0, "prizeName", "참가 포인트 5,000P")));

        assertTrue(allows("SPORTS_PREDICTION", rewards("상푼권", 2, "DRAW"), reordered));
    }

    @Test
    @DisplayName("모양이 수상하면 거부한다 — 판단이 안 되는 것은 막는 쪽이 안전하다")
    void 복주머니_모양이틀리면_거부한다() {
        Map<String, Object> notMaps = Map.of("pouches", List.of("데이터 5GB", "커피 쿠폰"));
        assertFalse(allows("LUCKY_POUCH", pouches("데이터 5GB", 30), notMaps));

        Map<String, Object> noIndex = Map.of("pouches", List.of(
                Map.of("winProbability", 30, "prizeName", "데이터 5GB"),
                Map.of("winProbability", 10, "prizeName", "커피 쿠폰")));
        assertFalse(allows("LUCKY_POUCH", pouches("데이터 5GB", 30), noIndex));
    }

    @Test
    @DisplayName("허용값과 금지값을 같이 보내면 거부한다 — 묶어서 통과시키지 않는다")
    void 섞어보내면_거부한다() {
        Map<String, Object> after = new HashMap<>(IMMEDIATE);
        after.put("prizeName", "새 경품");            // 허용
        after.put("winProbability", 90);              // 금지

        assertFalse(allows(IMMEDIATE, after));
    }

    @Test
    @DisplayName("금지값을 지우는 것도 변경이다 — 사라지면 검증이 다른 길로 간다")
    void 금지값_삭제도_거부한다() {
        Map<String, Object> after = new HashMap<>(IMMEDIATE);
        after.remove("winProbability");

        assertFalse(allows(IMMEDIATE, after));
    }

    @Test
    @DisplayName("설정에 없던 금지값을 새로 넣는 것도 거부한다")
    void 금지값_추가도_거부한다() {
        Map<String, Object> before = Map.of("resultMode", "GUARANTEED", "prizeName", "쿠폰");

        assertFalse(allows(before, with(before, "winProbability", 10)));
    }

    @Test
    void 참여_후_최대_당첨_인원_변경과_삭제를_거부한다() {
        Map<String, Object> before = with(IMMEDIATE, "maxWinnerCount", 10);

        assertFalse(allows(before, with(before, "maxWinnerCount", 20)));

        Map<String, Object> removed = new HashMap<>(before);
        removed.remove("maxWinnerCount");

        assertFalse(allows(before, removed));
    }

    @Test
    void 참여_후_최대_당첨_인원을_추가할_수_없다() {
        assertFalse(allows(
            IMMEDIATE,
            with(IMMEDIATE, "maxWinnerCount", 10)
        ));
    }

    @Test
    void 최대_당첨_인원이_같으면_통과한다() {
        Map<String, Object> before = with(IMMEDIATE, "maxWinnerCount", 10);

        assertTrue(allows(before, new HashMap<>(before)));
    }

    // ── 거푸집 ────────────────────────────────────────────────────

    private boolean allows(Map<String, Object> before, Map<String, Object> after) {
        return allows("BASIC", before, after);
    }

    private boolean allows(String gameCode, Map<String, Object> before, Map<String, Object> after) {
        return ParticipationConfigLock.allows(gameCode, gameCode, before, after);
    }

    private Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> copy = new HashMap<>(base);
        copy.put(key, value);
        return copy;
    }
}
