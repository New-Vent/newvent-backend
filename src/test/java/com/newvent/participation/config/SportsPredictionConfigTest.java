package com.newvent.participation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;

class SportsPredictionConfigTest {

    @Test
    void 전원_혜택과_정답자_추첨_설정을_읽는다() {
        SportsPredictionConfig result = SportsPredictionConfig.read(validConfig());

        assertThat(result.predictionOptions())
            .containsExactly("KOREA_WIN", "DRAW", "JAPAN_WIN");

        assertThat(result.announcementAt()).isEqualTo(
            OffsetDateTime.parse("2026-10-15T14:00:00+09:00")
        );

        assertThat(result.rewards()).containsExactly(
            new SportsPredictionConfig.Reward(
                "participation",
                "ALL_PARTICIPANTS",
                "ALL",
                0,
                "응원 5,000P"
            ),
            new SportsPredictionConfig.Reward(
                "prediction",
                "CORRECT_PREDICTIONS",
                "DRAW",
                30,
                "치킨 쿠폰"
            )
        );
    }

    @Test
    void 필수_설정이_없으면_거부한다() {
        assertInvalid(null);

        for (String key : List.of(
            "predictionOptions", "announcementAt", "rewards"
        )) {
            Map<String, Object> config = validConfig();
            config.remove(key);

            assertInvalid(config);
        }
    }

    @Test
    void 선택지가_비어있거나_중복되거나_문자열이_아니면_거부한다() {
        for (Object options : List.of(
            List.of(),
            List.of("KOREA_WIN", "KOREA_WIN"),
            List.of("KOREA_WIN", " "),
            List.of("KOREA_WIN", 123),
            "KOREA_WIN"
        )) {
            Map<String, Object> config = validConfig();
            config.put("predictionOptions", options);

            assertInvalid(config);
        }
    }

    @Test
    void 발표일이_올바른_OffsetDateTime이_아니면_거부한다() {
        for (Object announcementAt : List.of(
            "",
            "내일",
            "2026-10-15T14:00:00",
            123
        )) {
            Map<String, Object> config = validConfig();
            config.put("announcementAt", announcementAt);

            assertInvalid(config);
        }
    }

    @Test
    void 혜택이_비어있거나_객체가_아니면_거부한다() {
        for (Object rewards : List.of(
            List.of(),
            List.of("invalid"),
            "invalid"
        )) {
            Map<String, Object> config = validConfig();
            config.put("rewards", rewards);

            assertInvalid(config);
        }
    }

    @Test
    void rewardId가_중복되면_거부한다() {
        Map<String, Object> config = validConfig();
        config.put("rewards", List.of(drawReward(), drawReward()));

        assertInvalid(config);
    }

    @Test
    void 혜택의_필수값이_없으면_거부한다() {
        for (String key : List.of(
            "rewardId", "target", "selection",
            "winnerCount", "prizeName"
        )) {
            Map<String, Object> reward = drawReward();
            reward.remove(key);

            assertInvalid(withReward(reward));
        }
    }

    @Test
    void 모르는_대상이나_선정방식은_거부한다() {
        Map<String, Object> invalidTarget = drawReward();
        invalidTarget.put("target", "CORRECT");
        assertInvalid(withReward(invalidTarget));

        Map<String, Object> invalidSelection = drawReward();
        invalidSelection.put("selection", "RANDOM");
        assertInvalid(withReward(invalidSelection));
    }

    @Test
    void 추첨_인원은_1이상의_정수여야_한다() {
        for (Object count : List.of(
            0, -1, 1.5, "30", 2_147_483_648L
        )) {
            Map<String, Object> reward = drawReward();
            reward.put("winnerCount", count);

            assertInvalid(withReward(reward));
        }
    }

    @Test
    void 전원_지급은_winnerCount가_없어도_된다() {
        Map<String, Object> reward = drawReward();
        reward.put("selection", "ALL");
        reward.remove("winnerCount");

        SportsPredictionConfig result = SportsPredictionConfig.read(withReward(reward));

        assertThat(result.rewards().getFirst().winnerCount()).isZero();
    }

    @Test
    void 경품명이_비어있거나_문자열이_아니면_거부한다() {
        for (Object prizeName : List.of("", " ", 123)) {
            Map<String, Object> reward = drawReward();
            reward.put("prizeName", prizeName);

            assertInvalid(withReward(reward));
        }
    }

    private Map<String, Object> validConfig() {
        Map<String, Object> config = new LinkedHashMap<>();

        config.put(
            "predictionOptions",
            List.of("KOREA_WIN", "DRAW", "JAPAN_WIN")
        );
        config.put("announcementAt", "2026-10-15T14:00:00+09:00");
        config.put("rewards", List.of(
            Map.of(
                "rewardId", "participation",
                "target", "ALL_PARTICIPANTS",
                "selection", "ALL",
                "prizeName", "응원 5,000P"
            ),
            drawReward()
        ));

        return config;
    }

    private Map<String, Object> drawReward() {
        return new LinkedHashMap<>(Map.of(
            "rewardId", "prediction",
            "target", "CORRECT_PREDICTIONS",
            "selection", "DRAW",
            "winnerCount", 30,
            "prizeName", "치킨 쿠폰"
        ));
    }

    private Map<String, Object> withReward(Map<String, Object> reward) {
        Map<String, Object> config = validConfig();
        config.put("rewards", List.of(reward));
        return config;
    }

    private void assertInvalid(Map<String, Object> config) {
        assertThatThrownBy(() -> SportsPredictionConfig.read(config))
            .isInstanceOfSatisfying(
                ParticipationException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED)
            );
    }
}
