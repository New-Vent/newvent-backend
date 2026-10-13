package com.newvent.participation.config;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.service.ParticipationConfigReader;

/**
 * 스포츠 예측형 이벤트의 참여 설정
 * 원본 설정을 읽고 예측 선택지, 발표일, 혜택 설정을 검증한다.
 *
 * @param predictionOptions 사용자가 제출할 수 있는 예측값 목록
 * @param announcementAt 당첨 결과 발표 일시
 * @param rewards 혜택별 대상, 선정 방식, 경품 설정 목록
 */
public record SportsPredictionConfig(
    List<String> predictionOptions,
    OffsetDateTime announcementAt,
    List<Reward> rewards
) {

    /**
     * 혜택 하나의 지급 설정.
     *
     * @param rewardId 이벤트 내 혜택을 구분하는 고유 식별자
     * @param target 지급 대상 (ALL_PARTICIPANTS / CORRECT_PREDICTIONS)
     * @param selection 선정 방식 (ALL: 대상 전원 / DRAW: 대상 중 추첨)
     * @param winnerCount 추첨 인원수. ALL이면 사용하지 않으며 0으로 저장
     * @param prizeName 지급할 경품 이름
     */
    public record Reward(
        String rewardId,
        String target,
        String selection,
        int winnerCount,
        String prizeName
    ) {
    }

    public static SportsPredictionConfig read(Map<String, Object> config) {
        if (config == null) {
            throw invalidConfig();
        }

        List<String> predictionOptions = readPredictionOptions(config);
        OffsetDateTime announcementAt = readAnnouncementAt(config);
        List<Reward> rewards = readRewards(config);

        return new SportsPredictionConfig(
            List.copyOf(predictionOptions),
            announcementAt,
            List.copyOf(rewards)
        );
    }

    private static List<String> readPredictionOptions(Map<String, Object> config) {
        if (!(config.get("predictionOptions") instanceof List<?> options) || options.isEmpty()) {
            throw invalidConfig();
        }

        List<String> result = new ArrayList<>();
        Set<String> uniqueOptions = new HashSet<>();

        for (Object option : options) {
            String text = requiredText(option);

            // 중복 방지
            if (!uniqueOptions.add(text)) {
                throw invalidConfig();
            }

            result.add(text);
        }

        return result;
    }

    private static OffsetDateTime readAnnouncementAt(Map<String, Object> config) {
        String text = requiredText(config.get("announcementAt"));

        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException exception) {
            throw invalidConfig();
        }
    }

    private static List<Reward> readRewards(Map<String, Object> config) {
        if (!(config.get("rewards") instanceof List<?> items) || items.isEmpty()) {
            throw invalidConfig();
        }

        List<Reward> rewards = new ArrayList<>();
        Set<String> rewardIds = new HashSet<>();

        for (Object item : items) {
            if (!(item instanceof Map<?, ?> reward)) {
                throw invalidConfig();
            }

            String rewardId = requiredText(reward.get("rewardId"));
            String target = requiredText(reward.get("target"));
            String selection = requiredText(reward.get("selection"));

            if (!rewardIds.add(rewardId)) {
                throw invalidConfig();
            }

            if (!Set.of("ALL_PARTICIPANTS", "CORRECT_PREDICTIONS").contains(target)) {
                throw invalidConfig();
            }

            if (!Set.of("ALL", "DRAW").contains(selection)) {
                throw invalidConfig();
            }

            int winnerCount = "DRAW".equals(selection)
                ? ParticipationConfigReader.readInteger(
                reward.get("winnerCount"),
                1,
                Integer.MAX_VALUE,
                SportsPredictionConfig::invalidConfig
            )
                : 0;

            String prizeName = ParticipationConfigReader.readPrizeName(
                reward.get("prizeName"),
                SportsPredictionConfig::invalidConfig
            );

            rewards.add(new Reward(
                rewardId,
                target,
                selection,
                winnerCount,
                prizeName
            ));
        }

        return rewards;
    }

    private static String requiredText(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidConfig();
        }

        return text;
    }

    private static ParticipationException invalidConfig() {
        return new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED);
    }
}
