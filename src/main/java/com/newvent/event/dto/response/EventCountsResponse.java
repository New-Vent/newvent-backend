package com.newvent.event.dto.response;

// 관리자 이벤트 목록 화면의 상태별 건수 삭제된(휴지통) 이벤트는 집계에서 제외
public record EventCountsResponse(long total, long published, long draft, long ended) {
}
