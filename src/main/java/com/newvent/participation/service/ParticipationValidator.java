package com.newvent.participation.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.Game;
import com.newvent.participation.dto.request.ParticipationCreateRequest;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;

@Component
public class ParticipationValidator {

    public Map<String, Object> validate(
            EventGameConfig eventGameConfig,
            ParticipationCreateRequest request
    ) {
        Game game = eventGameConfig.getGame();

        if (game == null || !game.isActive() || game.getCode() == null) {
            throw new ParticipationException(ParticipationErrorCode.UNSUPPORTED_PARTICIPATION_TYPE);
        }

        ParticipationCreateRequest input = request == null
                ? new ParticipationCreateRequest(null, null, null)
                : request;

        Map<String, Object> config = eventGameConfig.getConfig();
        if (config == null) {
            throw invalidConfig();
        }

        return switch (game.getCode()) {
            case "BASIC" -> validateBasic(input);
            case "SPORTS_PREDICTION" -> validatePrediction(config, input);
            case "PRE_REGISTRATION" -> validatePhoneNumber(input);
            case "LUCKY_POUCH" -> validatePouch(config, input);
            default -> throw new ParticipationException(ParticipationErrorCode.UNSUPPORTED_PARTICIPATION_TYPE);
        };
    }

    private Map<String, Object> validateBasic(ParticipationCreateRequest input) {
        if (input.prediction() != null
                || input.phoneNumber() != null
                || input.pouchIndex() != null) {
            throw invalidInput();
        }

        return Map.of();
    }

    private Map<String, Object> validatePrediction(
            Map<String, Object> config,
            ParticipationCreateRequest input
    ) {
        Object value = config.get("predictionOptions");

        if (!(value instanceof List<?> options)
                || options.isEmpty()
                || options.stream().anyMatch(option ->
                !(option instanceof String text) || text.isBlank())) {
            throw invalidConfig();
        }

        if (input.prediction() == null
                || input.prediction().isBlank()
                || input.phoneNumber() != null
                || input.pouchIndex() != null) {
            throw invalidInput();
        }

        String prediction = input.prediction().trim();

        if (!options.contains(prediction)) {
            throw invalidInput();
        }

        return Map.of("prediction", prediction);
    }

    private Map<String, Object> validatePhoneNumber(
            ParticipationCreateRequest input
    ) {
        if (input.phoneNumber() == null
                || input.prediction() != null
                || input.pouchIndex() != null) {
            throw invalidInput();
        }

        String phoneNumber = input.phoneNumber().trim();

        // 국내 휴대전화 번호 : 숫자만 또는 하이픈을 포함한 형식
        if (!phoneNumber.matches(
                "^01[016789](?:\\d{7,8}|-\\d{3,4}-\\d{4})$")) {
            throw invalidInput();
        }

        return Map.of("phoneNumber", phoneNumber.replace("-", ""));
    }

    private Map<String, Object> validatePouch(
        Map<String, Object> config,
        ParticipationCreateRequest input
    ) {
        Object value = config.get("pouches");

        if (!(value instanceof List<?> pouches) || pouches.isEmpty()) {
            throw invalidConfig();
        }

        Set<Integer> indexes = new HashSet<>();

        for (Object item : pouches) {
            if (!(item instanceof Map<?, ?> pouch)) {
                throw invalidConfig();
            }

            int pouchIndex = positiveInteger(pouch.get("pouchIndex"));

            // 1부터 배열 길이까지 중복 없이 구성되어야 한다.
            if (pouchIndex > pouches.size() || !indexes.add(pouchIndex)) {
                throw invalidConfig();
            }

            validateProbability(pouch.get("winProbability"));

            ParticipationConfigReader.readPrizeName(
                pouch.get("prizeName"),
                this::invalidConfig
            );
        }

        if (input.pouchIndex() == null
            || input.prediction() != null
            || input.phoneNumber() != null
            || !indexes.contains(input.pouchIndex())) {
            throw invalidInput();
        }

        return Map.of("pouchIndex", input.pouchIndex());
    }

    // 복주머니 번호: 1 이상
    private int positiveInteger(Object value) {
        return ParticipationConfigReader.readInteger(
            value,
            1,
            Integer.MAX_VALUE,
            this::invalidConfig
        );
    }

    // 당첨 확률: 0~100
    private void validateProbability(Object value) {
        ParticipationConfigReader.readInteger(
            value,
            0,
            100,
            this::invalidConfig
        );
    }

    private ParticipationException invalidInput() {
        return new ParticipationException(ParticipationErrorCode.INVALID_SUBMITTED_DATA);
    }

    private ParticipationException invalidConfig() {
        return new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED);
    }
}
