package com.newvent.rag.dto.response;

import java.time.LocalDate;

// 주간 품질 추이 1행. 거리가 아니라 건수 기반이라 DB에 새로 쌓을 것 없이 읽는다
public record QualityTrendResponse(LocalDate weekStart, long totalCalls,
		long ragUsedCalls, long chunkCount) {

}
