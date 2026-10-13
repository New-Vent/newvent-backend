package com.newvent.rag.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.rag.domain.RagChunk;
import com.newvent.rag.domain.Vectors;
import com.newvent.rag.dto.response.ChunkResponse;
import com.newvent.rag.dto.response.PromptCandidate;
import com.newvent.rag.dto.response.SearchPreviewResponse;
import com.newvent.rag.repository.RagChunkRepository;
import com.newvent.registry.Block;

/**
 * 예시로 쓸 만한 청크를 골라줌. 모양 고정 : search(eventId, query, topK) -> List<RagChunk>.
 */
@Service
public class SimilarityService {

	private final EmbeddingClient embedding;
	private final RagChunkRepository chunks;

	public SimilarityService(EmbeddingClient embedding, RagChunkRepository chunks) {
		this.embedding = embedding;
		this.chunks = chunks;
	}

	public List<RagChunk> search(Long eventId, String query){
		return search(eventId, query, RagConstants.DEFAULT_TOP_K);
	}

	@Transactional(readOnly = true)
	public List<RagChunk> search(Long eventId, String query, int topK){
		if(query == null || query.isBlank()) {
			return List.of();
		}
		int limit = topK <= 0 ? RagConstants.DEFAULT_TOP_K : topK;
		float[] q = embedding.embed(query);
		// ★ 자기 이벤트는 빼고 찾는다. 자기 글을 예시로 가져오면 돌고 돌기 때문
		//   색인과 같은 임베딩 빈을 쓰므로 모델 이름이 항상 맞는다.
		return chunks.findSimilar(Vectors.toDb(q), embedding.modelName(),
				eventId, RagConstants.MAX_DISTANCE, limit);
	}

	/** 관리자 미리보기 — 임베딩은 1회만. search() 를 거치면 2회 나가서 Bedrock 비용 2배다 */
	@Transactional(readOnly = true)
	public SearchPreviewResponse searchPreview(Long eventId, String query, int topK) {
		if(query == null || query.isBlank()) {
			return new SearchPreviewResponse(eventId, query, topK, List.of());
		}
		float[] q = embedding.embed(query);
		List<RagChunk> hits = searchWithVector(eventId, q, topK);
		List<ChunkResponse> results = new ArrayList<>(hits.size());
		for(RagChunk c : hits) {
			double distance = 1.0 - Vectors.cosine(q, Vectors.fromDb(c.getEmbedding()));
			results.add(new ChunkResponse(c.getId(), c.getBlockKey(),
					c.getChunkIndex(), c.getContent(), distance));
		}
		return new SearchPreviewResponse(eventId, query, topK, results);
	}

	/**
	 * 기획용 전역 검색. SEED 포함, 자기 제외 없음 — 만들기 화면에서 이벤트 생기기 전에 쓴다.
	 * 생성 주입(search) 과 달리 운영 제외 규칙을 풀었으므로 편집 화면에서는 쓰지 않는다.
	 */
	@Transactional(readOnly = true)
	public List<RagChunk> searchPlanning(String query, int topK){
		if(query == null || query.isBlank()) {
			return List.of();
		}
		int limit = topK <= 0 ? RagConstants.DEFAULT_TOP_K : topK;
		float[] q = embedding.embed(query);
		return chunks.findSimilarIncludingSeed(Vectors.toDb(q), embedding.modelName(),
			-1L, RagConstants.MAX_DISTANCE, limit);
	}

	/** 벡터가 이미 계산된 경우 쓰는 내부 검색. search() 와 미리보기가 공유한다 */
	@Transactional(readOnly = true)
	List<RagChunk> searchWithVector(Long eventId, float[] queryVector, int topK) {
		int limit = topK <= 0 ? RagConstants.DEFAULT_TOP_K : topK;
		// ★ 자기 이벤트는 빼고 찾는다. 자기 글을 예시로 가져오면 돌고 돌기 때문
		//   색인과 같은 임베딩 빈을 쓰므로 모델 이름이 항상 맞는다.
		return chunks.findSimilar(Vectors.toDb(queryVector), embedding.modelName(),
				eventId, RagConstants.MAX_DISTANCE, limit);
	}

	/**
	 * 관리자 생성 보조. 유사 청크를 프롬프트 초안으로 바꿔 둘려줌
	 * 프론트는 이 중 하나를 고르면 채팅창에 그대로 넣음
	 */
	@Transactional(readOnly = true)
	/**
	 * eventId 가 null 이면 기획 모드 — 자기 제외 없이 SEED 포함 전역에서 찾는다.
	 * 만들기 화면에서 이벤트 생기기 전에 부른다. search() (생성 주입용)는 손대지 않는다.
	 */
	public List<PromptCandidate> recommendPrompts(Long eventId, String query, int topK){
		List<RagChunk> hits = (eventId == null)
			? searchPlanning(query, topK)
			: search(eventId, query, topK);
		List<PromptCandidate> out = new ArrayList<>(hits.size());
		for (RagChunk h : hits) {
			// ★ 색인된 블록은 llmBlocks() 뿐 shape() 이 비어 있지 않음
			Block block = Block.find(h.getBlockKey()).orElse(null);
			String guide = block != null ? block.shape() : "";
			out.add(new PromptCandidate(h.getEvent().getId(), h.getEvent().getTitle(),
	                h.getBlockKey(), guide + " (예시: " + h.getContent() + ")"));
		}
		return out;
	}
}
