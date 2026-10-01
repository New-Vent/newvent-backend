package com.newvent.rag.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.rag.domain.RagChunk;

/**
 * rag_chunks 읽기 전담
 *
 * ★ 유사도 검색은 네이티브 쿼리. JPQL에 벡터 연산자가 없음.
 *   distance = 1 - similarity. min-score 0.6 -> maxDistance 0.4 로 바꿔서 넘김
 *
 * ★ excludeEventId 는 필수 (없으면 - 1)
 *   네이티브 쿼리에 null 바인딩은 타입을 못 잡는다.
 *   자기 이벤트 예시로 자기 자신을 재사용하면 문제가 굳음
 */
public interface RagChunkRepository extends JpaRepository<RagChunk, Long>{

	@Query(value = """
			select * from rag_chunks
			 where embedding_model = :model
			   and event_id <> :excludeEventId
			   and embedding <=> cast(:query as vector) <= :maxDistance
			 order by embedding <=> cast(:query as vector)
			 limit :limit
			""", nativeQuery = true)
	List<RagChunk> findSimilar(@Param("query") String query,
			@Param("model") String model,
			@Param("excludeEventId") long excludeEventId,
			@Param("maxDistance") double maxDistance,
			@Param("limit") int limit);
	
	// 같은 버전을 다시 색인할 때 묵은 청크를 지우는 용도
	void deleteByEvent_IdandVersion_Id(Long eventId, Long versionId);
	
	// 청크가 하나라도 들어간 버전 수
	@Query("select count(distinct r.version.id) from RagChunk r where r.event.id = :eventId")
	long countIndexedVersions(@Param("eventId") Long eventId);
	
	// 전체 청크 수
	@Query("select count(r) from RagChunk r where r.event.id = :eventId")
	long countChunks(@Param("eventId") Long eventId);
	
	// 마지막 색인 시각. 비어 있으면 null
	@Query("select max(r.createdAt) from RagChunk r where r.event.id = :eventId")
	Instant lastIndexedAt(@Param("eventId") Long eventId);
}
