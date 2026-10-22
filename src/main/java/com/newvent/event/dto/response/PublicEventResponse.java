package com.newvent.event.dto.response;

import java.time.OffsetDateTime;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.user.domain.MembershipGrade;

/**
 * 공개 이벤트 상세.
 *
 *   publishedHtml 은 조각이 아니라 <b>완전한 문서</b>다 — /e/{id} 가 내보내는 것과 같다
 *   (doctype · head 의 event.css · body 의 data-event-id · data-game · event-runtime.js).
 *   다른 페이지에 innerHTML 로 끼우지 말고 iframe 문서로 띄운다.
 *   저장된 HTML 은 슬롯(기간·참여링크)이 빈 상태다 — 저장할 때 채우면 그 값이 굳어서
 *   이벤트 기간만 고쳐도 화면이 안 바뀐다(Slots.fill 주석 참고).
 *   그래서 채우는 건 보여줄 때이고, 이 DTO 는 <b>이미 채워진 것을 받는다.</b>
 *   엔티티에서 직접 꺼내지 않는다 — 꺼내면 슬롯이 빈 채로 나간다.
 */
public record PublicEventResponse(
        Long id,
        String title,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        EventStatus status,
        MembershipGrade grade,
        String url,
        String publishedHtml,
        boolean closingSoon) {

    /**
     * @param publishedHtml 슬롯을 채워 완전한 문서로 감싼 게시 페이지. 게시 버전이 없으면 null
     *                      (PublicEventService.publishedPageOf 가 만든다)
     */
    public static PublicEventResponse from(Event event, boolean closingSoon, String publishedHtml) {
        return new PublicEventResponse(
                event.getId(), event.getTitle(), event.getStartDate(), event.getEndDate(),
                event.getStatus(), event.getGrade(), event.getUrl(), publishedHtml, closingSoon);
    }
}
