package com.newvent.rag.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.EventVersion;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.rag.domain.RagChunk;
import com.newvent.rag.dto.response.IndexStatusResponse;
import com.newvent.rag.repository.RagChunkRepository;
import com.newvent.rag.service.RagChunkingService.Chunk;

/**
 * 임베딩 파이프라인 진입점. 자르기·저장·현황을 맡는다.
 * 찾기는 SimilarityService 몫이다.
 */
@Service
public class EmbeddingService {

	private final EventVersionRepository versions;
	private final RagChunkRepository chunks;
	private final RagChunkingService chunking;
	private final EmbeddingClient embedding;
	
	public EmbeddingService(EventVersionRepository versions, RagChunkRepository chunks,
	        RagChunkingService chunking, EmbeddingClient embedding) {
		this.versions = versions;
		this.chunks = chunks;
		this.chunking = chunking;
		this.embedding = embedding;
	}
	
	/**
	 * 수동 재색인. versionId 가 없으면 그 이벤트 전체 버전을 돌린다.
	 * 컨트롤러가 부르는 창구는 이것 하나로 통일
	 */
	@Transactional
	public int reindex(Long eventId, Long versionId) {
		if(versionId != null) {
			return index(eventId, versionId);
		}
		int total = 0;
		// 자기 호출이라 트랜잭션이 새로 안 생김. 버전이 많아도 중간 상태는 밖에서 안 보인다.
		for(EventVersion v : versions.findByEventIdOrderByVersionNoAsc(eventId)) {
			total += index(eventId, v.getId());
		}
		return total;
	}
	
	// 한 버전 색인하고 저장한 개수를 돌려준다. 다른 이벤트의 버전이면 404
	@Transactional
	public int index(Long eventId, Long versionId) {
		EventVersion version = versions.findByIdAndEventId(versionId, eventId)
				.orElseThrow(() -> new EventException(EventErrorCode.VERSION_NOT_FOUND));
		List<Chunk> parts = chunking.chunk(version.getHtmlContent());
		// 지우기와 저장을 한 트랜잭션으로 묶음. 중간에 실패하면 둘 다 없던 일이 되어 절반만 저장되는 일은 없음.
		chunks.deleteByEvent_IdandVersion_Id(eventId, versionId);
		List<RagChunk> entities = new ArrayList<>(parts.size());
		for(Chunk p : parts) {
			float[] vec = embedding.embed(p.content());
			entities.add(RagChunk.create(version.getEvent(), version, p.blockKey(),
					p.chunkIndex(), p.content(), embedding.modelName(), vec, Instant.now()));
		}
		chunks.saveAll(entities);
		return entities.size();
	}
	
	// 색인 현황. 못 센 실패 건수는 두지 않는다 - 들어가지 않은 버전은 전부 미완료로 잡음.
	@Transactional(readOnly = true)
	public IndexStatusResponse getStatus(Long eventId) {
		long total = versions.countByEventId(eventId);
		long indexed = chunks.countIndexedVersions(eventId);
		return new IndexStatusResponse(eventId, total, indexed, total - indexed,
				chunks.countChunks(eventId), chunks.lastIndexedAt(eventId));
	}
}
