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
