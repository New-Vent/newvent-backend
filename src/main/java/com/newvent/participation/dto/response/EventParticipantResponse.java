package com.newvent.participation.dto.response;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.newvent.participation.domain.EventParticipation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 관리자가 보는 참여 기록 한 건.
 *
 * ★ {@code MyParticipationResponse} 와 방향이 반대다 — 그쪽은 「내가 어느 이벤트에
 *   참여했나」라서 이벤트 정보를 담고, 이쪽은 「이 이벤트에 누가 참여했나」라서 사람을 담는다.
 *   같은 DTO 로 합치면 절반이 항상 null 이다.
 */
@Builder
public record EventParticipantResponse(

        @Schema(description = "참여 기록 id")
        Long participationId,

        @Schema(description = "참여자 id")
        Long userId,

        @Schema(description = "참여자 이름")
        String userName,

        @Schema(description = "참여 시각")
        OffsetDateTime participatedAt,

        /**
         * 참여할 때 사용자가 낸 값.
         *
         * ★ 방식마다 모양이 다르다 — 기본형은 {@code {}}, 스포츠 예측형은 {@code {"prediction":"2-1"}},
         *   사전예약형은 {@code {"phoneNumber":"01012345678"}}, 복주머니형은 {@code {"pouchIndex":3}}.
         *   그래서 칸을 고정하지 않고 저장된 그대로 준다.
         *
         * ★★ 전화번호가 여기 들어간다. 경품을 보내려면 관리자가 봐야 하는 값이지만,
         *   <b>이 응답은 개인정보다.</b> 이벤트를 만든 관리자만 볼 수 있게 막혀 있고
         *   (EventOwnerCheck), 로그에 본문을 남기지 않는다.
         */
        @Schema(description = "참여 입력. 방식마다 모양이 다르다 (prediction · phoneNumber · pouchIndex). "
                + "기본형은 빈 객체")
        Map<String, Object> submittedData,

        @Schema(description = "결과 상태. WON · LOST · PENDING. 결과가 없는 방식이면 null",
                allowableValues = {"WON", "LOST", "PENDING"})
        String resultStatus,

        /**
         * 대표 경품명.
         *
         * ★★ 혜택이 여러 개인 방식에서는 <b>이 칸만 보면 안 된다.</b> 당첨된 혜택 중
         *   <b>첫 번째 것</b>만 들어온다 — 발표 배치가 {@code ORDER BY position LIMIT 1} 로
         *   고른 값이다. 혜택이 둘인 이벤트에서 「참가 포인트」와 「상품권」에 모두 당첨된
         *   사람이 목록에는 「참가 포인트」로만 보이고, <b>상품권 당첨자가 사라진다.</b>
         *   경품 발송 목록으로 쓰려면 {@link #rewards} 를 봐야 한다.
         *
         * ★ 그래도 남겨 둔다 — 혜택이 하나인 방식(기본형 · 복주머니)에서는 이게 답이고,
         *   기존 화면이 이 칸을 그리고 있다.
         */
        @Schema(description = "대표 경품명. 당첨이 아니면 null. "
                + "★ 혜택이 여러 개면 첫 당첨 혜택 하나만 들어온다 — 발송 목록은 rewards 를 보라")
        String prizeName,

        /**
         * 혜택별 결과. <b>경품 발송 목록은 이것으로 만든다.</b>
         *
         * ★ 왜 필요한가 — 한 사람이 여러 혜택에 당첨될 수 있다
         *   승부 예측형은 혜택을 여러 개 둔다 (예: 참여자 전원에게 포인트 + 정답자 중
         *   추첨으로 상품권). 결과는 {@code result_data.rewards} 에 혜택별로 쌓이는데,
         *   최상위 {@code prizeName} 에는 그중 하나만 올라온다.
         *
         * ★ 혜택이 하나인 방식에서는 비어 있다 — 그 방식들은 {@code result_data} 에
         *   rewards 를 쓰지 않는다. 화면은 비어 있으면 prizeName 을 그리면 된다.
         */
        @Schema(description = "혜택별 결과. 혜택이 여러 개인 방식(승부 예측형)에서 채워진다. "
                + "혜택이 하나인 방식은 빈 목록 — 그때는 prizeName 을 쓴다")
        List<RewardResult> rewards
) {
    /**
     * 혜택 하나의 결과.
     *
     * @param rewardId   설정의 혜택 식별자 — 어느 혜택인지 가리킨다
     * @param status     WON · LOST
     * @param prizeName  당첨이면 경품명, 아니면 null
     */
    @Schema(description = "혜택 하나의 결과")
    public record RewardResult(String rewardId, String status, String prizeName) { }

    public static EventParticipantResponse from(EventParticipation participation) {
        Map<String, Object> resultData = participation.getResultData();

        return EventParticipantResponse.builder()
                .participationId(participation.getId())
                .userId(participation.getUser().getId())
                .userName(participation.getUser().getName())
                .participatedAt(participation.getCreatedAt())
                .submittedData(participation.getSubmittedData())
                .resultStatus(readString(resultData, "status"))
                .prizeName(readString(resultData, "prizeName"))
                .rewards(readRewards(resultData))
                .build();
    }

    /**
     * {@code result_data.rewards} 를 읽는다.
     *
     * ★ 모양이 아니면 빈 목록이다. 손으로 고친 result_data 하나 때문에 목록 조회 전체가
     *   500 이 되면 안 된다 — 경품을 보내려고 여는 화면이다.
     * ★ rewardId 가 없는 칸은 버린다. 어느 혜택인지 모르면 발송에 쓸 수 없다.
     */
    private static List<RewardResult> readRewards(Map<String, Object> resultData) {
        Object value = resultData == null ? null : resultData.get("rewards");

        if (!(value instanceof List<?> items)) {
            return List.of();
        }

        List<RewardResult> out = new ArrayList<>(items.size());

        for (Object item : items) {
            if (!(item instanceof Map<?, ?> reward)) {
                continue;
            }
            String rewardId = text(reward.get("rewardId"));
            if (rewardId == null) {
                continue;
            }
            out.add(new RewardResult(rewardId, text(reward.get("status")), text(reward.get("prizeName"))));
        }
        return List.copyOf(out);
    }

    private static String readString(Map<String, Object> data, String key) {
        return text(data == null ? null : data.get(key));
    }

    private static String text(Object value) {
        return value instanceof String s ? s : null;
    }
}
