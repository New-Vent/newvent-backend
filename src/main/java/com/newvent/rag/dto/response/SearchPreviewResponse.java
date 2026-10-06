package com.newvent.rag.dto.response;

import java.util.List;

// 검색 미리보기. 빈 쿼리면 results 가 비어 있다.
public record SearchPreviewResponse(Long eventId, String query, int topK,
		List<ChunkResponse> results) {

}
