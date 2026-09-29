package com.newvent.rag.repository;

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
			   and evet_id <> :excludeEventId
			   and embedding <=> cast(:query as vector) <= :maxDistance
			 order by embedding <=> cast(:query as vector)
			 limit :limit
			""", nativeQuery = true)
	List<RagChunk> findSimilar(@Param("query") String query,
			@Param("model") String model,
			@Param("excludeEventId") long excludeEventId,
			@Param("maxDistance") double maxDistance,
			@Param("limit") int limit);
}
