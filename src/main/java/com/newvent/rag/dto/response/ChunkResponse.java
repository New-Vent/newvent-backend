package com.newvent.rag.dto.response;

// 청크 1건. distance가 작을수록 질문과 가깝다
public record ChunkResponse(Long chunkId, String blockKey, int chunkIndex,
		String content, double distance) {

}
