package com.newvent.participation.dto.response;

import com.newvent.common.response.PageResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자용 참여자 목록.
 *
 * ★ 요약을 같이 주는 이유 — 목록의 totalElements 로는 못 만든다.
 *   그건 <b>필터가 걸린 수</b>다. 「당첨 12명」을 보려고 resultStatus=WON 을 걸면
 *   전체 참여 수가 12로 보이게 된다. 요약은 필터와 무관하게 전체를 센다.
 */
public record EventParticipantListResponse(

        @Schema(description = "필터와 무관한 전체 집계 — 참여 수 · 당첨 수 · 결과 대기 수")
        ParticipationSummaryResponse summary,

        @Schema(description = "참여 기록. 최신순")
        PageResponse<EventParticipantResponse> participants
) {
}
