package com.newvent.participation.service;

import java.util.ArrayList;
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

/**
 * 참여 설정과 참여 입력을 검증한다.
 *
 * ★ 두 입구가 있다
 *   validateConfig(game, config)        설정만 — 관리자 설정 API 가 쓴다. 입력이 없다
 *   validate(eventGameConfig, request)  설정 + 입력 — 참여 API 가 쓴다
 *
 *   설정 검증을 따로 뺀 이유는 **관리자 설정 API 가 입력 없이 설정만 봐야** 하기 때문이다.
 *   규칙을 두 벌로 쓰면 갈라지므로, 참여 경로도 같은 함수를 먼저 부른다.
 */
@Component
public class ParticipationValidator {

    /**
     * 설정만 검증한다. 관리자가 설정을 저장할 때 쓴다.
     *
     * ★ PRE_REGISTRATION 은 설정이 없다 — 전화번호는 입력이고 설정이 아니다.
     */
    public void validateConfig(Game game, Map<String, Object> config) {
        requireSupported(game);

        if (config == null) {
            throw invalidConfig();
        }

        switch (game.getCode()) {
            case "BASIC" -> validateBasicConfig(config);
            case "SPORTS_PREDICTION" -> readPredictionOptions(config);
            case "PRE_REGISTRATION" -> { }
            case "LUCKY_POUCH" -> readPouchIndexes(config);
            default -> throw unsupported();
        }
    }

    public Map<String, Object> validate(
            EventGameConfig eventGameConfig,
            ParticipationCreateRequest request
    ) {
        Game game = eventGameConfig.getGame();
        Map<String, Object> config = eventGameConfig.getConfig();

        requireSupported(game);

        if (config == null) {
            throw invalidConfig();
        }

        ParticipationCreateRequest input = request == null
                ? new ParticipationCreateRequest(null, null, null)
                : request;

        return switch (game.getCode()) {
            case "BASIC" -> {
                validateBasicConfig(config);
                yield basicInput(input);
            }
            case "SPORTS_PREDICTION" -> predictionInput(readPredictionOptions(config), input);
            case "PRE_REGISTRATION" -> phoneNumberInput(input);
            case "LUCKY_POUCH" -> pouchInput(readPouchIndexes(config), input);
            default -> throw unsupported();
        };
    }

    // ── 설정 ──────────────────────────────────────────────────────

    /**
     * 기본형 설정.
     *
     * ★ 이 검증이 **없었다.** validateBasic 이 config 를 아예 보지 않는데
     *   ParticipationResultProcessor.processBasic 은 resultMode 를 읽고,
     *   IMMEDIATE 면 winProbability·prizeName 을, GUARANTEED 면 prizeName 을 요구한다.
     *   그래서 {"resultMode":"IMMEDIATE"} 같은 반쪽 설정이 저장되면
     *   **검증은 통과하고 참여할 때마다 409 가 났다.**
     *
     * ★ resultMode 가 없는 것은 정상이다 — 결과 없이 참여만 기록하는 이벤트다
     *   (processBasic 이 빈 result_data 를 돌려준다).
     */
    private void validateBasicConfig(Map<String, Object> config) {
        Object resultMode = config.get("resultMode");

        if (resultMode == null) {
            return;
        }

        switch (resultMode.toString()) {
            case "DELAYED" -> { }
            case "IMMEDIATE" -> {
                validateProbability(config.get("winProbability"));
                requirePrizeName(config.get("prizeName"));
            }
            case "GUARANTEED" -> requirePrizeName(config.get("prizeName"));
            default -> throw invalidConfig();
        }
    }

    /** 예측 선택지. 비어 있거나 빈 문자열이 섞이면 거부한다. */
    private List<String> readPredictionOptions(Map<String, Object> config) {
        Object value = config.get("predictionOptions");

        if (!(value instanceof List<?> options)
                || options.isEmpty()
                || options.stream().anyMatch(option ->
                !(option instanceof String text) || text.isBlank())) {
            throw invalidConfig();
        }

        List<String> result = new ArrayList<>(options.size());
        for (Object option : options) {
            result.add((String) option);
        }
        return result;
    }

    /**
     * 복주머니 번호 집합. 1 부터 배열 길이까지 중복 없이 구성되어야 한다.
     *
     * ★ 선택하지 않은 주머니도 확률·경품명이 유효해야 한다 — 설정 전체가 온전해야
     *   어느 주머니를 골라도 추첨이 된다.
     */
    private Set<Integer> readPouchIndexes(Map<String, Object> config) {
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

            if (pouchIndex > pouches.size() || !indexes.add(pouchIndex)) {
                throw invalidConfig();
            }

            validateProbability(pouch.get("winProbability"));
            requirePrizeName(pouch.get("prizeName"));
        }

        return indexes;
    }

    // ── 입력 ──────────────────────────────────────────────────────

    private Map<String, Object> basicInput(ParticipationCreateRequest input) {
        if (input.prediction() != null
                || input.phoneNumber() != null
                || input.pouchIndex() != null) {
            throw invalidInput();
        }

        return Map.of();
    }

    private Map<String, Object> predictionInput(
            List<String> options,
            ParticipationCreateRequest input
    ) {
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

    private Map<String, Object> phoneNumberInput(ParticipationCreateRequest input) {
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

    private Map<String, Object> pouchInput(
            Set<Integer> indexes,
            ParticipationCreateRequest input
    ) {
        if (input.pouchIndex() == null
                || input.prediction() != null
                || input.phoneNumber() != null
                || !indexes.contains(input.pouchIndex())) {
            throw invalidInput();
        }

        return Map.of("pouchIndex", input.pouchIndex());
    }

    // ── 공통 ──────────────────────────────────────────────────────

    private void requireSupported(Game game) {
        if (game == null || !game.isActive() || game.getCode() == null) {
            throw unsupported();
        }
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

    private void requirePrizeName(Object value) {
        ParticipationConfigReader.readPrizeName(value, this::invalidConfig);
    }

    private ParticipationException unsupported() {
        return new ParticipationException(ParticipationErrorCode.UNSUPPORTED_PARTICIPATION_TYPE);
    }

    private ParticipationException invalidInput() {
        return new ParticipationException(ParticipationErrorCode.INVALID_SUBMITTED_DATA);
    }

    private ParticipationException invalidConfig() {
        return new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED);
    }
}
