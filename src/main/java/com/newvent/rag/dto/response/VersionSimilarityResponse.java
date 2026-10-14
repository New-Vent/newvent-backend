package com.newvent.rag.dto.response;

import java.util.List;

// 유사 버전 1건. similarity가 클수록 기준 버전과 비슷하다
// eventId·eventTitle 은 query 모드(여러 이벤트)에서 어느 것인지 보여주려고 뒤에 붙였다
public record VersionSimilarityResponse(Long versionId, int versionNo, double similarity,
		List<ChunkResponse> topChunks, Long eventId, String eventTitle) {

}
