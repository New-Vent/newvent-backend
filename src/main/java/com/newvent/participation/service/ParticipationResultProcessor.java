package com.newvent.participation.service;

import java.security.SecureRandom;
import java.util.List;
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

    public Map<String, Object> process(EventGameConfig eventGameConfig, Map<String, Object> submittedData) {
        Map<String, Object> config = eventGameConfig.getConfig();

        if (config == null) {
            throw invalidConfig();
        }

        return switch (eventGameConfig.getGame().getCode()) {
            case "BASIC" -> processBasic(config);
            case "LUCKY_POUCH" -> drawPouch(config, submittedData);
            case "SPORTS_PREDICTION" -> Map.of("status", "PENDING");
            case "PRE_REGISTRATION" -> Map.of();
            default -> throw new ParticipationException(ParticipationErrorCode.UNSUPPORTED_PARTICIPATION_TYPE);
        };
    }

    private Map<String, Object> processBasic(Map<String, Object> config) {
        Object resultMode = config.get("resultMode");

        if (resultMode == null) {
            return Map.of();
        }

        return switch (resultMode.toString()) {
            case "DELAYED" -> Map.of("status", "PENDING");
            case "IMMEDIATE" -> draw(config);
            case "GUARANTEED" -> guaranteed(config);
            default -> throw invalidConfig();
        };
    }

    private Map<String, Object> guaranteed(Map<String, Object> config) {
        String prizeName = ParticipationConfigReader.readPrizeName(config.get("prizeName"), this::invalidConfig);

        return Map.of(
            "status", "WON",
            "prizeName", prizeName
        );
    }

    private Map<String, Object> draw(Map<?, ?> config) {
        int winProbability = readProbability(config.get("winProbability"));

        String prizeName = ParticipationConfigReader.readPrizeName(
            config.get("prizeName"),
            this::invalidConfig
        );

        // 0~99 중 하나를 뽑는다. 확률이 30이면 0~29가 당첨이다.
        boolean won = random.nextInt(100) < winProbability;

        if (won) {
            return Map.of(
                "status", "WON",
                "prizeName", prizeName
            );
        }

        return Map.of("status", "LOST");
    }

    private Map<String, Object> drawPouch(
        Map<String, Object> config,
        Map<String, Object> submittedData
    ) {
        Object value = config.get("pouches");

        if (!(value instanceof List<?> pouches) || pouches.isEmpty()) {
            throw invalidConfig();
        }

        // Validator가 검증해 저장한 번호를 사용한다.
        if (submittedData == null
            || !(submittedData.get("pouchIndex") instanceof Integer selectedIndex)) {
            throw new ParticipationException(ParticipationErrorCode.INVALID_SUBMITTED_DATA);
        }

        Map<?, ?> selectedPouch = null;

        for (Object item : pouches) {
            if (!(item instanceof Map<?, ?> pouch)) {
                throw invalidConfig();
            }

            int pouchIndex = readPouchIndex(pouch.get("pouchIndex"));

            if (pouchIndex == selectedIndex) {
                if (selectedPouch != null) {
                    throw invalidConfig();
                }

                selectedPouch = pouch;
            }
        }

        if (selectedPouch == null) {
            throw invalidConfig();
        }

        return draw(selectedPouch);
    }

    private int readPouchIndex(Object value) {
        return ParticipationConfigReader.readInteger(
            value,
            1,
            Integer.MAX_VALUE,
            this::invalidConfig
        );
    }

    private int readProbability(Object value) {
        return ParticipationConfigReader.readInteger(value, 0, 100, this::invalidConfig);
    }

    private ParticipationException invalidConfig() {
        return new ParticipationException(
                ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED);
    }
}
