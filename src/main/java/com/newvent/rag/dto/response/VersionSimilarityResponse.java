package com.newvent.rag.dto.response;

import java.util.List;

// 유사 버전 1건. similarity가 클수록 기준 버전과 비슷하다
public record VersionSimilarityResponse(Long versionId, int versionNo, double similarity,
		List<ChunkResponse> topChunks) {

}
