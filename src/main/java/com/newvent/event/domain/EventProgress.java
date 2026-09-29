package com.newvent.event.domain;

// 목록 조회의 "진행상태별 조회" 필터 전용 값. EventStatus(영속 컬럼)와 달리 저장되지 않고,
// startDate/endDate와 현재 시각을 비교해서 매 요청마다 계산한다.
public enum EventProgress {
    UPCOMING,
    ONGOING,
    ENDED
}
