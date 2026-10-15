package com.newvent.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.Game;
import com.newvent.participation.dto.request.ParticipationCreateRequest;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;

class ParticipationValidatorTest {

    private final ParticipationValidator validator = new ParticipationValidator();

    @Test
    void 기본형은_본문없이_참여한다() {
        assertEquals(Map.of(), validator.validate(config("BASIC", Map.of()), null));
    }

    @Test
    void 설정된_예측값을_저장한다() {
        EventGameConfig config = predictionConfig();

        assertEquals(
                Map.of("prediction", "HOME_WIN"),
                validator.validate(
                        config,
                        new ParticipationCreateRequest("HOME_WIN", null, null)));
    }

    @Test
    void 예측값이_없거나_선택지에_없으면_거부한다() {
        EventGameConfig config = predictionConfig();

        assertInputError(config, null);
        assertInputError(
                config,
                new ParticipationCreateRequest("UNKNOWN", null, null));
    }

    @Test
    void 전화번호는_하이픈을_제거해서_저장한다() {
        assertEquals(
                Map.of("phoneNumber", "01012345678"),
                validator.validate(
                        config("PRE_REGISTRATION", Map.of()),
                        new ParticipationCreateRequest(null, "010-1234-5678", null)));
    }

    @Test
    void 전화번호가_없거나_형식이_틀리면_거부한다() {
        EventGameConfig config = config("PRE_REGISTRATION", Map.of());

        assertInputError(config, null);
        assertInputError(
                config,
                new ParticipationCreateRequest(null, "123", null));
    }

    @Test
    void 복주머니는_설정된_모든_번호를_허용한다() {
        EventGameConfig config = config(
            "LUCKY_POUCH",
            Map.of("pouches", List.of(
                pouch(1, 30, "커피 쿠폰"),
                pouch(2, 10, "치킨 쿠폰"),
                pouch(3, 50, "편의점 상품권"))));

        for (int index : List.of(1, 2, 3)) {
            assertEquals(
                Map.of("pouchIndex", index),
                validator.validate(
                    config,
                    new ParticipationCreateRequest(null, null, index)));
        }
    }

    @Test
    void 복주머니가_누락되거나_범위를_벗어나면_거부한다() {
        EventGameConfig config = config(
            "LUCKY_POUCH",
            Map.of("pouches", List.of(
                pouch(1, 30, "커피 쿠폰"),
                pouch(2, 10, "치킨 쿠폰"))));

        assertInputError(config, null);

        for (int index : List.of(0, -1, 3)) {
            assertInputError(
                config,
                new ParticipationCreateRequest(null, null, index));
        }

        assertInputError(
            config,
            new ParticipationCreateRequest("HOME_WIN", null, 1));

        assertInputError(
            config,
            new ParticipationCreateRequest(null, "01012345678", 1));
    }

    @Test
    void 복주머니_배열이_없거나_잘못되면_거부한다() {
        for (Map<String, Object> values : List.<Map<String, Object>>of(
            Map.of(),
            Map.of("pouchCount", 3),
            Map.of("pouches", List.of()),
            Map.of("pouches", "invalid"),
            Map.of("pouches", List.of("invalid")))) {
            assertPouchConfigError(values);
        }
    }

    @Test
    void 복주머니_번호가_중복되거나_누락되면_거부한다() {
        assertPouchConfigError(Map.of("pouches", List.of(
            pouch(1, 30, "커피 쿠폰"),
            pouch(1, 10, "치킨 쿠폰"))));

        assertPouchConfigError(Map.of("pouches", List.of(
            pouch(1, 30, "커피 쿠폰"),
            pouch(3, 10, "치킨 쿠폰"))));
    }

    @Test
    void 복주머니_번호는_양의_정수여야_한다() {
        for (Object index : List.of(0, -1, 1.5, "1")) {
            assertPouchConfigError(Map.of("pouches", List.of(pouch(index, 30, "커피 쿠폰"))));
        }

        assertPouchConfigError(Map.of(
            "pouches", List.of(Map.of(
                "winProbability", 30,
                "prizeName", "커피 쿠폰"))));
    }

    @Test
    void 복주머니_확률은_0부터_100까지_허용한다() {
        EventGameConfig config = config(
            "LUCKY_POUCH",
            Map.of("pouches", List.of(
                pouch(1, 0, "커피 쿠폰"),
                pouch(2, 100, "치킨 쿠폰"))));

        for (int index : List.of(1, 2)) {
            assertEquals(
                Map.of("pouchIndex", index),
                validator.validate(
                    config,
                    new ParticipationCreateRequest(null, null, index)));
        }
    }

    @Test
    void 복주머니_확률이_잘못되면_거부한다() {
        for (Object probability : List.of(-1, 101, 30.5, "30")) {
            assertPouchConfigError(Map.of("pouches", List.of(pouch(1, probability, "커피 쿠폰"))));
        }

        assertPouchConfigError(Map.of(
            "pouches", List.of(Map.of(
                "pouchIndex", 1,
                "prizeName", "커피 쿠폰"))));
    }

    @Test
    void 선택하지_않은_주머니도_경품명이_유효해야_한다() {
        for (Object prizeName : List.of("", "   ", 123)) {
            assertPouchConfigError(Map.of("pouches", List.of(
                pouch(1, 30, "커피 쿠폰"),
                pouch(2, 10, prizeName))));
        }

        assertPouchConfigError(Map.of("pouches", List.of(
            pouch(1, 30, "커피 쿠폰"),
            Map.of("pouchIndex", 2, "winProbability", 10))));
    }

    private Map<String, Object> pouch(
        Object index,
        Object probability,
        Object prizeName
    ) {
        return Map.of(
            "pouchIndex", index,
            "winProbability", probability,
            "prizeName", prizeName);
    }

    private void assertPouchConfigError(Map<String, Object> values) {
        ParticipationException exception = assertThrows(
            ParticipationException.class,
            () -> validator.validate(
                config("LUCKY_POUCH", values),
                new ParticipationCreateRequest(null, null, 1)));

        assertEquals(
            ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED,
            exception.getErrorCode());
    }

    @Test
    void 지원하지_않는_참여방식은_거부한다() {
        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> validator.validate(config("UNKNOWN", Map.of()), null));

        assertEquals(
                ParticipationErrorCode.UNSUPPORTED_PARTICIPATION_TYPE,
                exception.getErrorCode());
    }

    @Test
    void 참여방식과_무관한_입력은_거부한다() {
        assertInputError(
                predictionConfig(),
                new ParticipationCreateRequest(
                        "HOME_WIN", "01012345678", null));
    }

    // ── validateConfig — 설정만 검증하는 입구 (관리자 설정 API 가 쓴다) ──────

    @Test
    @DisplayName("기본형 설정에 resultMode 가 없으면 통과한다 — 결과 없이 참여만 기록하는 이벤트")
    void 기본형_resultMode_없으면_통과() {
        validator.validateConfig(game("BASIC"), Map.of());
    }

    @Test
    @DisplayName("IMMEDIATE 인데 winProbability 나 prizeName 이 없으면 거부한다 — 전에는 통과하고 참여할 때 터졌다")
    void 기본형_IMMEDIATE_필수값_누락은_거부() {
        for (Map<String, Object> values : List.<Map<String, Object>>of(
                Map.of("resultMode", "IMMEDIATE"),
                Map.of("resultMode", "IMMEDIATE", "winProbability", 30),
                Map.of("resultMode", "IMMEDIATE", "prizeName", "쿠폰"),
                Map.of("resultMode", "IMMEDIATE", "winProbability", 101, "prizeName", "쿠폰"),
                Map.of("resultMode", "IMMEDIATE", "winProbability", 30, "prizeName", " "))) {
            assertConfigError("BASIC", values);
        }
    }

    @Test
    void 기본형_IMMEDIATE_는_확률과_경품명이_모두_있으면_통과() {
        validator.validateConfig(
                game("BASIC"),
                Map.of("resultMode", "IMMEDIATE", "winProbability", 30, "prizeName", "데이터 5GB"));
    }

    @Test
    void 기본형_GUARANTEED_는_경품명이_필요하다() {
        assertConfigError("BASIC", Map.of("resultMode", "GUARANTEED"));
        validator.validateConfig(game("BASIC"), Map.of("resultMode", "GUARANTEED", "prizeName", "쿠폰"));
    }

    @Test
    @DisplayName("DELAYED 는 winnerCount·announcementAt·prizeName 이 모두 필요하다 — 없으면 야간 스케줄러가 매일 실패한다")
    void 기본형_DELAYED_는_필수값이_있다() {
        for (Map<String, Object> values : List.<Map<String, Object>>of(
                Map.of("resultMode", "DELAYED"),
                Map.of("resultMode", "DELAYED", "winnerCount", 10),
                Map.of("resultMode", "DELAYED", "winnerCount", 10, "prizeName", "쿠폰"),
                Map.of("resultMode", "DELAYED", "winnerCount", 0,
                        "prizeName", "쿠폰", "announcementAt", "2026-12-01T00:00:00+09:00"),
                Map.of("resultMode", "DELAYED", "winnerCount", 10,
                        "prizeName", "쿠폰", "announcementAt", "내일"))) {
            assertConfigError("BASIC", values);
        }

        validator.validateConfig(game("BASIC"), Map.of(
                "resultMode", "DELAYED", "winnerCount", 10, "prizeName", "쿠폰",
                "announcementAt", "2026-12-01T00:00:00+09:00"));
    }

    @Test
    void 모르는_resultMode_는_거부한다() {
        assertConfigError("BASIC", Map.of("resultMode", "SOMEDAY"));
    }

    @Test
    @DisplayName("사전예약형은 설정이 없다 — 전화번호는 입력이고 설정이 아니다")
    void 사전예약형은_설정이_없다() {
        validator.validateConfig(game("PRE_REGISTRATION"), Map.of());
    }

    @Test
    void 설정검증도_복주머니_규칙을_그대로_적용한다() {
        assertConfigError("LUCKY_POUCH", Map.of("pouches", List.of()));
        assertConfigError("LUCKY_POUCH", Map.of(
                "pouches", List.of(Map.of("pouchIndex", 1, "winProbability", 10))));
        validator.validateConfig(game("LUCKY_POUCH"), Map.of(
                "pouches", List.of(
                        Map.of("pouchIndex", 1, "winProbability", 10, "prizeName", "상품권"),
                        Map.of("pouchIndex", 2, "winProbability", 100, "prizeName", "데이터"))));
    }

    @Test
    void 설정이_null_이면_거부한다() {
        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> validator.validateConfig(game("BASIC"), null));
        assertEquals(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED, exception.getErrorCode());
    }

    @Test
    void 비활성_방식은_설정도_거부한다() {
        Game game = mock(Game.class);
        when(game.getCode()).thenReturn("BASIC");
        when(game.isActive()).thenReturn(false);

        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> validator.validateConfig(game, Map.of()));
        assertEquals(ParticipationErrorCode.UNSUPPORTED_PARTICIPATION_TYPE, exception.getErrorCode());
    }

    private Game game(String code) {
        Game game = mock(Game.class);
        when(game.getCode()).thenReturn(code);
        when(game.isActive()).thenReturn(true);
        return game;
    }

    private void assertConfigError(String code, Map<String, Object> values) {
        Game game = game(code);
        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> validator.validateConfig(game, values));
        assertEquals(
                ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED,
                exception.getErrorCode(),
                () -> "설정 오류여야 합니다: " + values);
    }

    private EventGameConfig predictionConfig() {
        return config(
            "SPORTS_PREDICTION",
            Map.of(
                "predictionOptions",
                List.of("HOME_WIN", "DRAW", "AWAY_WIN"),
                "announcementAt",
                "2026-10-15T14:00:00+09:00",
                "rewards",
                List.of(Map.of(
                    "rewardId", "prediction",
                    "target", "CORRECT_PREDICTIONS",
                    "selection", "DRAW",
                    "winnerCount", 30,
                    "prizeName", "치킨 쿠폰"
                ))
            )
        );
    }

    private EventGameConfig config(
            String code,
            Map<String, Object> values
    ) {
        Game game = mock(Game.class);
        when(game.getCode()).thenReturn(code);
        when(game.isActive()).thenReturn(true);

        EventGameConfig config = mock(EventGameConfig.class);
        when(config.getGame()).thenReturn(game);
        when(config.getConfig()).thenReturn(values);

        return config;
    }

    private void assertInputError(
            EventGameConfig config,
            ParticipationCreateRequest request
    ) {
        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> validator.validate(config, request));

        assertEquals(
                ParticipationErrorCode.INVALID_SUBMITTED_DATA,
                exception.getErrorCode());
    }
}
