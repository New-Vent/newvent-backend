package com.newvent.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

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
    void 복주머니는_설정된_범위의_양끝을_허용한다() {
        EventGameConfig config =
                config("LUCKY_POUCH", Map.of("pouchCount", 3));

        for (int index : List.of(1, 3)) {
            assertEquals(
                    Map.of("pouchIndex", index),
                    validator.validate(
                            config,
                            new ParticipationCreateRequest(null, null, index)));
        }
    }

    @Test
    void 복주머니가_누락되거나_범위를_벗어나면_거부한다() {
        EventGameConfig config =
                config("LUCKY_POUCH", Map.of("pouchCount", 3));

        assertInputError(config, null);
        assertInputError(
                config,
                new ParticipationCreateRequest(null, null, 0));
        assertInputError(
                config,
                new ParticipationCreateRequest(null, null, 4));
    }

    @Test
    void 잘못된_복주머니_설정은_거부한다() {
        for (Map<String, Object> values : List.<Map<String, Object>>of(
                Map.of(),
                Map.of("pouchCount", 0),
                Map.of("pouchCount", 2.5))) {
            ParticipationException exception = assertThrows(
                    ParticipationException.class,
                    () -> validator.validate(
                            config("LUCKY_POUCH", values),
                            new ParticipationCreateRequest(null, null, 1)));

            assertEquals(
                    ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED,
                    exception.getErrorCode());
        }
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

    private EventGameConfig predictionConfig() {
        return config(
                "SPORTS_PREDICTION",
                Map.of(
                        "predictionOptions",
                        List.of("HOME_WIN", "DRAW", "AWAY_WIN")));
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
