package com.newvent.participation.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;

@Component
public class ParticipationResultProcessor {

    private final SecureRandom random;

    public ParticipationResultProcessor() {
        this.random = new SecureRandom();
    }

    // 테스트에서는 난수를 지정해서 당첨·미당첨을 확인한다.
    ParticipationResultProcessor(SecureRandom random) {
        this.random = random;
    }

    public Map<String, Object> process(EventGameConfig eventGameConfig) {
        Map<String, Object> config = eventGameConfig.getConfig();

        if (config == null) {
            throw invalidConfig();
        }

        return switch (eventGameConfig.getGame().getCode()) {
            case "BASIC" -> processBasic(config);
            case "LUCKY_POUCH" -> draw(config);
            case "SPORTS_PREDICTION", "PRE_REGISTRATION" -> Map.of();
            default -> throw new ParticipationException(ParticipationErrorCode.UNSUPPORTED_PARTICIPATION_TYPE);
        };
    }

    private Map<String, Object> processBasic(Map<String, Object> config) {
        Object resultMode = config.get("resultMode");

        if (resultMode == null || "DELAYED".equals(resultMode)) {
            return Map.of();
        }

        return switch (resultMode.toString()) {
            case "IMMEDIATE" -> draw(config);
            case "GUARANTEED" -> guaranteed(config);
            default -> throw invalidConfig();
        };
    }

    private Map<String, Object> guaranteed(Map<String, Object> config) {
        Object value = config.get("prizeName");

        if (!(value instanceof String prizeName) || prizeName.isBlank()) {
            throw invalidConfig();
        }

        return Map.of(
                "status", "WON",
                "prizeName", prizeName.trim());
    }

    private Map<String, Object> draw(Map<String, Object> config) {
        int winProbability = readProbability(config.get("winProbability"));

        Object value = config.get("prizeName");
        if (!(value instanceof String prizeName) || prizeName.isBlank()) {
            throw invalidConfig();
        }

        // 0~99 중 하나를 뽑는다. 확률이 30이면 0~29가 당첨이다.
        boolean won = random.nextInt(100) < winProbability;

        if (won) {
            return Map.of(
                    "status", "WON",
                    "prizeName", prizeName.trim());
        }

        return Map.of("status", "LOST");
    }

    private int readProbability(Object value) {
        if (!(value instanceof Number number)) {
            throw invalidConfig();
        }

        try {
            int probability = new BigDecimal(number.toString()).intValueExact();

            if (probability < 0 || probability > 100) {
                throw invalidConfig();
            }

            return probability;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw invalidConfig();
        }
    }

    private ParticipationException invalidConfig() {
        return new ParticipationException(
                ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED);
    }
}
