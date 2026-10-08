package com.newvent.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.Game;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;

class ParticipationResultProcessorTest {

    private final SecureRandom random = mock(SecureRandom.class);

    private final ParticipationResultProcessor processor =
        new ParticipationResultProcessor(random);

    @Test
    void 선택한_복주머니의_확률을_적용한다() {
        when(random.nextInt(100)).thenReturn(20);

        EventGameConfig config = config(
            "LUCKY_POUCH",
            Map.of("pouches", List.of(
                pouch(1, 30, "커피 쿠폰"),
                pouch(2, 10, "치킨 쿠폰"))));

        assertEquals(
            Map.of("status", "WON", "prizeName", "커피 쿠폰"),
            processor.process(config, Map.of("pouchIndex", 1)));

        assertEquals(
            Map.of("status", "LOST"),
            processor.process(config, Map.of("pouchIndex", 2)));
    }

    @Test
    void 배열_순서와_관계없이_선택한_번호의_경품을_반환한다() {
        when(random.nextInt(100)).thenReturn(0);

        EventGameConfig config = config(
            "LUCKY_POUCH",
            Map.of("pouches", List.of(
                pouch(2, 100, "치킨 쿠폰"),
                pouch(1, 100, "커피 쿠폰"))));

        assertEquals(
            Map.of("status", "WON", "prizeName", "커피 쿠폰"),
            processor.process(config, Map.of("pouchIndex", 1)));

        assertEquals(
            Map.of("status", "WON", "prizeName", "치킨 쿠폰"),
            processor.process(config, Map.of("pouchIndex", 2)));
    }

    @Test
    void 확률이_30이면_난수_29는_당첨이고_30은_미당첨이다() {
        when(random.nextInt(100)).thenReturn(29, 30);

        EventGameConfig config = config(
            "LUCKY_POUCH",
            Map.of("pouches", List.of(pouch(1, 30, "커피 쿠폰"))));

        assertEquals(
            Map.of("status", "WON", "prizeName", "커피 쿠폰"),
            processor.process(config, Map.of("pouchIndex", 1)));

        assertEquals(
            Map.of("status", "LOST"),
            processor.process(config, Map.of("pouchIndex", 1)));
    }

    @Test
    void 확률이_0이면_미당첨이고_100이면_당첨이다() {
        when(random.nextInt(100)).thenReturn(0, 99);

        EventGameConfig config = config(
            "LUCKY_POUCH",
            Map.of("pouches", List.of(
                pouch(1, 0, "커피 쿠폰"),
                pouch(2, 100, "치킨 쿠폰"))));

        assertEquals(
            Map.of("status", "LOST"),
            processor.process(config, Map.of("pouchIndex", 1)));

        assertEquals(
            Map.of("status", "WON", "prizeName", "치킨 쿠폰"),
            processor.process(config, Map.of("pouchIndex", 2)));
    }

    @Test
    void 기본형도_즉시추첨_설정이면_결과를_반환한다() {
        when(random.nextInt(100)).thenReturn(99);

        assertEquals(
            Map.of("status", "WON", "prizeName", "커피 쿠폰"),
            processor.process(
                config("BASIC", Map.of(
                    "resultMode", "IMMEDIATE",
                    "winProbability", 100,
                    "prizeName", "커피 쿠폰")),
                Map.of()));
    }

    @Test
    void 추첨하지_않는_방식은_기존_결과를_유지한다() {
        assertEquals(
            Map.of(),
            processor.process(config("BASIC", Map.of()), Map.of()));

        assertEquals(
            Map.of("status", "PENDING"),
            processor.process(
                config("BASIC", Map.of("resultMode", "DELAYED")),
                Map.of()));

        assertEquals(
            Map.of(),
            processor.process(
                config("PRE_REGISTRATION", Map.of()),
                Map.of("phoneNumber", "01012345678")));

        assertEquals(
            Map.of("status", "PENDING"),
            processor.process(
                config("SPORTS_PREDICTION", Map.of()),
                Map.of("prediction", "HOME_WIN")));

        verifyNoInteractions(random);
    }

    @Test
    void 기본형_전원지급은_추첨없이_당첨결과를_반환한다() {
        assertEquals(
            Map.of("status", "WON", "prizeName", "커피 쿠폰"),
            processor.process(
                config("BASIC", Map.of(
                    "resultMode", "GUARANTEED",
                    "prizeName", "커피 쿠폰")),
                Map.of()));

        verifyNoInteractions(random);
    }

    @Test
    void 전원지급도_경품명이_없으면_거부한다() {
        assertConfigError(
            config("BASIC", Map.of("resultMode", "GUARANTEED")),
            Map.of());

        verifyNoInteractions(random);
    }

    @Test
    void 선택한_복주머니의_확률이_잘못되면_추첨하지_않는다() {
        for (Object probability : List.of(-1, 101, 30.5, "30")) {
            assertConfigError(
                config("LUCKY_POUCH", Map.of(
                    "pouches",
                    List.of(pouch(1, probability, "커피 쿠폰")))),
                Map.of("pouchIndex", 1));
        }

        verifyNoInteractions(random);
    }

    @Test
    void 선택한_복주머니의_경품명이_없으면_추첨하지_않는다() {
        assertConfigError(
            config("LUCKY_POUCH", Map.of(
                "pouches",
                List.of(Map.of(
                    "pouchIndex", 1,
                    "winProbability", 30)))),
            Map.of("pouchIndex", 1));

        verifyNoInteractions(random);
    }

    @Test
    void 선택한_번호가_설정에_없거나_중복되면_추첨하지_않는다() {
        assertConfigError(
            config("LUCKY_POUCH", Map.of(
                "pouches", List.of(pouch(1, 30, "커피 쿠폰")))),
            Map.of("pouchIndex", 2));

        assertConfigError(
            config("LUCKY_POUCH", Map.of(
                "pouches", List.of(
                    pouch(1, 30, "커피 쿠폰"),
                    pouch(1, 10, "치킨 쿠폰")))),
            Map.of("pouchIndex", 1));

        verifyNoInteractions(random);
    }

    @Test
    void 선택_번호가_없으면_입력_오류를_반환한다() {
        EventGameConfig config = config(
            "LUCKY_POUCH",
            Map.of("pouches", List.of(pouch(1, 30, "커피 쿠폰"))));

        ParticipationException exception = assertThrows(
            ParticipationException.class,
            () -> processor.process(config, Map.of()));

        assertEquals(
            ParticipationErrorCode.INVALID_SUBMITTED_DATA,
            exception.getErrorCode());

        verifyNoInteractions(random);
    }

    private Map<String, Object> pouch(
        int index,
        Object probability,
        String prizeName
    ) {
        return Map.of(
            "pouchIndex", index,
            "winProbability", probability,
            "prizeName", prizeName);
    }

    private void assertConfigError(
        EventGameConfig config,
        Map<String, Object> submittedData
    ) {
        ParticipationException exception = assertThrows(
            ParticipationException.class,
            () -> processor.process(config, submittedData));

        assertEquals(
            ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED,
            exception.getErrorCode());
    }

    private EventGameConfig config(
        String gameCode,
        Map<String, Object> values
    ) {
        Game game = mock(Game.class);
        when(game.getCode()).thenReturn(gameCode);

        EventGameConfig config = mock(EventGameConfig.class);
        when(config.getGame()).thenReturn(game);
        when(config.getConfig()).thenReturn(values);

        return config;
    }
}
