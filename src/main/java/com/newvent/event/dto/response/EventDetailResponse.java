package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.user.domain.MembershipGrade;

import io.swagger.v3.oas.annotations.media.Schema;

public record EventDetailResponse(
        Long id,
        String name,
        EventStatus status,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime updatedAt,
        @Schema(description = "템플릿 키. 템플릿 없이 만들었으면 null")
        String template,
        @Schema(description = "참여할 수 있는 최소 멤버십 등급")
        MembershipGrade grade,
        /**
         * 게시 버전의 HTML — <b>사용자가 보는 것과 같은 것</b>이다.
         *
         * ★★ 전에는 저장된 조각을 그대로 줬다(event.completedHtml()). 그래서 슬롯이
         *   하나도 채워지지 않았다 — 같은 이벤트인데 미리보기에는 기간이 보이고
         *   여기는 빈칸이었고, 참여 주소도 없었다.
         *   지금은 공개 상세와 같은 {@code PublicEventService.publishedHtmlOf} 를 지난다.
         *
         */
        @Schema(description = "게시 버전의 HTML. 기간·참여 주소 슬롯이 채워진, "
                + "사용자가 보는 것과 같은 HTML. 게시한 적 없으면 null")
        String completedHtml,
        @Schema(description = "게시 중인 버전 번호. 게시한 적 없으면 null")
        Integer publishedVersionNo,
        @Schema(description = "마감임박. 게시 중이고 이미 시작했으며 종료 3일 전부터 종료 시각까지면 true")
        boolean closingSoon,
        @Schema(description = "요청한 관리자가 만든 이벤트면 true. 수정·게시·삭제 버튼 노출 기준")
        boolean ownedByMe
) {
    /**
     * @param completedHtml 슬롯을 채운 게시 HTML. 게시 버전이 없으면 null —
     *                      {@code PublicEventService.publishedHtmlOf} 가 만든다.
     *                      <b>여기서 직접 event.completedHtml() 을 부르지 않는다</b> —
     *                      그건 슬롯이 빈 생 조각이고, 참여 설정도 볼 수 없다.
     */
    public static EventDetailResponse from(
            Event event, String completedHtml, boolean closingSoon, boolean ownedByMe) {
        return new EventDetailResponse(
                event.getId(),
                event.getTitle(),
                event.getStatus(),
                event.getStartDate(),
                event.getEndDate(),
                event.getUpdatedAt(),
                event.templateCode(),
                event.getGrade(),
                completedHtml,
                event.getPublishedVersion() == null ? null : event.getPublishedVersion().getVersionNo(),
                closingSoon,
                ownedByMe);
    }
}
