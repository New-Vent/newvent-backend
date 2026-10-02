package com.newvent.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.Game;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;

public class ParticipationResultProcessorTest {

    private final SecureRandom random = mock(SecureRandom.class);

    private final ParticipationResultProcessor processor =
            new ParticipationResultProcessor(random);

    @Test
    void 확률이_30이면_난수_29는_당첨이다() {
        when(random.nextInt(100)).thenReturn(29);

        Map<String, Object> result = processor.process(
                config(
                        "LUCKY_POUCH",
                        Map.of(
                                "winProbability", 30,
                                "prizeName", "커피 쿠폰")));

        assertEquals(
                Map.of("status", "WON", "prizeName", "커피 쿠폰"),
                result);
    }

    @Test
    void 확률이_30이면_난수_30은_미당첨이다() {
        when(random.nextInt(100)).thenReturn(30);

        Map<String, Object> result = processor.process(
                config(
                        "LUCKY_POUCH",
                        Map.of(
                                "winProbability", 30,
                                "prizeName", "커피 쿠폰")));

        assertEquals(Map.of("status", "LOST"), result);
    }

    @Test
    void 확률이_0이면_당첨되지_않는다() {
        when(random.nextInt(100)).thenReturn(0);

        assertEquals(
                Map.of("status", "LOST"),
                processor.process(
                        config(
                                "LUCKY_POUCH",
                                Map.of(
                                        "winProbability", 0,
                                        "prizeName", "커피 쿠폰"))));
    }

    @Test
    void 기본형도_즉시추첨_설정이면_결과를_반환한다() {
        when(random.nextInt(100)).thenReturn(99);

        assertEquals(
                Map.of("status", "WON", "prizeName", "커피 쿠폰"),
                processor.process(
                        config(
                                "BASIC",
                                Map.of(
                                        "resultMode", "IMMEDIATE",
                                        "winProbability", 100,
                                        "prizeName", "커피 쿠폰"))));
    }

    @Test
    void 기본형의_빈설정은_추첨하지_않는다() {
        assertEquals(
                Map.of(),
                processor.process(config("BASIC", Map.of())));

        verifyNoInteractions(random);
    }

    @Test
    void 기본형_DELAYED는_결과_대기를_반환한다() {
        assertEquals(
                Map.of("status", "PENDING"),
                processor.process(config("BASIC", Map.of("resultMode", "DELAYED")))
        );

        verifyNoInteractions(random);
    }

    @Test
    void 사전예약형은_빈_결과를_반환한다() {
        assertEquals(
                Map.of(),
                processor.process(config("PRE_REGISTRATION", Map.of()))
        );

        verifyNoInteractions(random);
    }

    @Test
    void 스포츠_예측형은_결과_대기를_반환한다() {
        assertEquals(
                Map.of("status", "PENDING"),
                processor.process(config("SPORTS_PREDICTION", Map.of()))
        );

        verifyNoInteractions(random);
    }

    @Test
    void 확률이_잘못되면_추첨하지_않는다() {
        for (Object probability : new Object[]{-1, 101, 30.5, "30"}) {
            ParticipationException exception = assertThrows(
                    ParticipationException.class,
                    () -> processor.process(
                            config(
                                    "LUCKY_POUCH",
                                    Map.of(
                                            "winProbability", probability,
                                            "prizeName", "커피 쿠폰"))));

            assertEquals(
                    ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED,
                    exception.getErrorCode());
        }

        verifyNoInteractions(random);
    }

    @Test
    void 경품명이_없으면_추첨하지_않는다() {
        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> processor.process(
                        config(
                                "LUCKY_POUCH",
                                Map.of("winProbability", 30))));

        assertEquals(
                ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED,
                exception.getErrorCode());

        verifyNoInteractions(random);
    }

    @Test
    void 기본형_전원지급은_추첨없이_당첨결과를_반환한다() {
        Map<String, Object> result = processor.process(
                config(
                        "BASIC",
                        Map.of(
                                "resultMode", "GUARANTEED",
                                "prizeName", "커피 쿠폰")));

        assertEquals(
                Map.of("status", "WON", "prizeName", "커피 쿠폰"),
                result);

        verifyNoInteractions(random);
    }

    @Test
    void 전원지급도_경품명이_없으면_거부한다() {
        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> processor.process(
                        config(
                                "BASIC",
                                Map.of("resultMode", "GUARANTEED"))));

        assertEquals(
                ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED,
                exception.getErrorCode());

        verifyNoInteractions(random);
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
