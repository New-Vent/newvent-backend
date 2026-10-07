package com.newvent.rag.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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
	void deleteByEventIdAndVersionId(Long eventId, Long versionId);

	// 청크가 하나라도 들어간 버전 수
	@Query("select count(distinct r.version.id) from RagChunk r where r.event.id = :eventId")
	long countIndexedVersions(@Param("eventId") Long eventId);

	// 전체 청크 수
	@Query("select count(r) from RagChunk r where r.event.id = :eventId")
	long countChunks(@Param("eventId") Long eventId);

	// 마지막 색인 시각. 비어 있으면 null
	@Query("select max(r.createdAt) from RagChunk r where r.event.id = :eventId")
	Instant lastIndexedAt(@Param("eventId") Long eventId);

	// 주간 청크 집계 (품질 추이 대시보드용). 주 경계는 llm 쪽 sumRagUsageByWeek 와 같다
	@Query(value = """
			select		date_trunc('week', created_at at time zone 'Asia/Seoul')::date as "weekStart",
						count(*) as "chunkCount"
			  from		rag_chunks
			 where		created_at >= :from
			 group by	1
			 order by	1 desc
			""", nativeQuery = true)
	List<WeeklyChunkRow> countChunksByWeek(@Param("from") Instant from);

	// 주간 집계 1행. record 가 아니라 읽기 전용 투영 (테이블 X)
	interface WeeklyChunkRow {
		LocalDate getWeekStart();
		long getChunkCount();
	}

	// 같은 이벤트 안 다른 버전에서 유사 청크 검색 (유사 버전 탐색용)
	// findSimilar 와 반대로 event_id = 로 묶고 version_id <> 로 기준 버전을 거른다.
	// version_id 가 없는 청크는 버전 탐색 대상이 아니라 DB에서 제외한다
	@Transactional(readOnly = true)
	@Query(value = """
			select * from rag_chunks
			 where embedding_model = :model
			   and event_id = :eventId
			   and version_id is not null and version_id <> :excludeVersionId
			   and embedding <=> cast(:query as vector) <= :maxDistance
			 order by embedding <=> cast(:query as vector)
			 limit :limit
			""", nativeQuery = true)
	List<RagChunk> findSimilarInEvent(@Param("query") String query,
			@Param("model") String model,
			@Param("eventId") long eventId,
			@Param("excludeVersionId") long excludeVersionId,
			@Param("maxDistance") double maxDistance,
			@Param("limit") int limit);

	// 기준 버전의 색인 청크. 없으면 빈 목록
	@Transactional(readOnly = true)
	@Query("select r from RagChunk r where r.event.id = :eventId and r.version.id = :versionId order by r.chunkIndex")
	List<RagChunk> findChunksOfVersion(@Param("eventId") Long eventId,
			@Param("versionId") Long versionId);
}
