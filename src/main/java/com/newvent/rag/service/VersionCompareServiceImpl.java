package com.newvent.rag.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.EventVersion;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.rag.domain.RagChunk;
import com.newvent.rag.domain.Vectors;
import com.newvent.rag.dto.response.ChunkResponse;
import com.newvent.rag.dto.response.VersionSimilarityResponse;
import com.newvent.rag.repository.RagChunkRepository;

/**
 * 유사 버전 탐색. 모양 고정 : similarVersions(eventId, versionId, topK) -> 유사도 내림차순.
 *
 * ★ 기준 버전의 색인된 청크 벡터를 그대로 쿼리로 쓴다. 임베딩을 새로 부르지 않는다.
 *   Bedrock 호출이 없어서 미리보기보다 싸고, 색인 때 쓴 벡터와 완전히 같은 공간이다.
 * ★ 같은 이벤트 안만 본다. findSimilar(event_id <>) 와 반대로 event_id = 로 묶는다.
 */
@Service
public class VersionCompareServiceImpl implements VersionCompareService {

	private final EventVersionRepository versions;
	private final RagChunkRepository chunks;
	private final EmbeddingClient embedding;

	public VersionCompareServiceImpl(EventVersionRepository versions, RagChunkRepository chunks,
			EmbeddingClient embedding) {
		this.versions = versions;
		this.chunks = chunks;
		this.embedding = embedding;
	}

	@Transactional(readOnly = true)
	@Override
	public List<VersionSimilarityResponse> similarVersions(Long eventId, Long versionId, int topK) {
		EventVersion target = versions.findByIdAndEventId(versionId, eventId)
				.orElseThrow(() -> new EventException(EventErrorCode.VERSION_NOT_FOUND));
		List<RagChunk> targetChunks = chunks.findChunksOfVersion(eventId, target.getId());
		if (targetChunks.isEmpty()) {
			return List.of();
		}
		int limit = topK <= 0 ? RagConstants.DEFAULT_TOP_K : topK;
		// 청크별 최소 거리로 합친다. 같은 청크가 여러 번 잡혀도 가장 가까운 것만 남긴다
		Map<Long, Double> best = new HashMap<>();
		Map<Long, RagChunk> byId = new HashMap<>();
		for (RagChunk t : targetChunks) {
			float[] q = Vectors.fromDb(t.getEmbedding());
			List<RagChunk> hits = chunks.findSimilarInEvent(Vectors.toDb(q), embedding.modelName(),
					eventId, target.getId(), RagConstants.MAX_DISTANCE, limit * 3);
			for (RagChunk h : hits) {
				if (h.getVersion() == null || target.getId().equals(h.getVersion().getId())) {
					continue;
				}
				double distance = 1.0 - Vectors.cosine(q, Vectors.fromDb(h.getEmbedding()));
				best.merge(h.getId(), distance, Math::min);
				byId.putIfAbsent(h.getId(), h);
			}
		}
		// 버전별로 묶는다. 버전 유사도 = 소속 청크 중 가장 가까운 것 → 유사도
		Map<Long, List<RagChunk>> perVersion = new HashMap<>();
		Map<Long, Double> versionBest = new HashMap<>();
		Map<Long, Integer> versionNos = new HashMap<>();
		for (Map.Entry<Long, Double> e : best.entrySet()) {
			RagChunk h = byId.get(e.getKey());
			Long vid = h.getVersion().getId();
			perVersion.computeIfAbsent(vid, k -> new ArrayList<>()).add(h);
			versionBest.merge(vid, e.getValue(), Math::min);
			versionNos.putIfAbsent(vid, h.getVersion().getVersionNo());
		}
		return versionBest.entrySet().stream()
				.sorted(Map.Entry.comparingByValue())
				.limit(limit)
				.map(e -> {
					Long vid = e.getKey();
					double similarity = 1.0 - e.getValue();
					List<ChunkResponse> top = perVersion.get(vid).stream()
							.sorted(Comparator.comparingDouble(c -> best.get(c.getId())))
							.limit(2)
							.map(c -> new ChunkResponse(c.getId(), c.getBlockKey(),
									c.getChunkIndex(), c.getContent(), best.get(c.getId())))
							.toList();
					return new VersionSimilarityResponse(vid, versionNos.get(vid), similarity, top);
				})
				.toList();
	}

	@Override
	public VersionDiff compare(Long eventId, Long oldVersionId, Long newVersionId) {
		throw new UnsupportedOperationException("버전 간 비교는 b4(공개 API)와 함께 구현한다");
	}
}
