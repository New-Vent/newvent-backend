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
 *   distance = 1 - similarity. MIN_SCORE 미만(거리 초과)은 버린다.
 *
 * ★ excludeEventId 는 필수 (없으면 - 1)
 *   네이티브 쿼리에 null 바인딩은 타입을 못 잡는다.
 *   자기 이벤트 예시로 자기 자신을 재사용하면 문제가 굳음
 */
public interface RagChunkRepository extends JpaRepository<RagChunk, Long>{

	@Query(value = """
			select c.* from rag_chunks c
			 join events e on e.id = c.event_id
			 where c.embedding_model = :model
			   and c.event_id <> :excludeEventId
			   and e.deleted_at is null
			   and e.title not like 'SEED:%'
			   and c.embedding <=> cast(:query as vector) <= :maxDistance
			 order by c.embedding <=> cast(:query as vector)
			 limit :limit
			""", nativeQuery = true)
	List<RagChunk> findSimilar(@Param("query") String query,
			@Param("model") String model,
			@Param("excludeEventId") long excludeEventId,
			@Param("maxDistance") double maxDistance,
			@Param("limit") int limit);

	// 기획용 전역 검색. SEED 제외를 푼 것 빼고 findSimilar 과 같다.
	// 지금은 시드 29건이 유일한 데이터라 제외하면 빈 목록이 나온다 (실데이터 쌓이면 제외로 전환).
	@Query(value = """
		select c.* from rag_chunks c
		 join events e on e.id = c.event_id
		 where c.embedding_model = :model
		   and c.event_id <> :excludeEventId
		   and e.deleted_at is null
		   and c.embedding <=> cast(:query as vector) <= :maxDistance
		 order by c.embedding <=> cast(:query as vector)
		 limit :limit
		""", nativeQuery = true)
	List<RagChunk> findSimilarIncludingSeed(@Param("query") String query,
		@Param("model") String model,
		@Param("excludeEventId") long excludeEventId,
		@Param("maxDistance") double maxDistance,
		@Param("limit") int limit);

	// 기획용 버전 단위 조회 (2단계). 1단계에서 버전별 최소 거리 순으로 상위 버전 id 를 고르고,
	// 2단계에서 그 버전들의 상위 2개 청크를 가져온다. 한 쿼리로 묶으면 min_dist 동점 경계에서
	// LIMIT 가 선택된 버전의 2번째 청크를 자를 수 있어 분리한다 (동점은 version_id 순으로 정리).
	@Query(value = """
		select c.version_id from rag_chunks c
		 join events e on e.id = c.event_id
		 where c.embedding_model = :model
		   and c.version_id is not null
		   and c.event_id <> :excludeEventId
		   and e.deleted_at is null
		   and c.embedding <=> cast(:query as vector) <= :maxDistance
		 group by c.version_id
		 order by min(c.embedding <=> cast(:query as vector)), c.version_id
		 limit :limit
		""", nativeQuery = true)
	List<Long> findTopVersionIds(@Param("query") String query,
		@Param("model") String model,
		@Param("excludeEventId") long excludeEventId,
		@Param("maxDistance") double maxDistance,
		@Param("limit") int limit);

	@Query(value = """
		select * from (
		  select c.*,
		         row_number() over (partition by c.version_id order by c.embedding <=> cast(:query as vector)) as rn
		    from rag_chunks c
		   where c.embedding_model = :model
		     and c.version_id in (:versionIds)
		     and c.embedding <=> cast(:query as vector) <= :maxDistance
		) ranked
		 where rn <= 2
		 order by version_id, rn
		""", nativeQuery = true)
	List<RagChunk> findTopChunksOfVersions(@Param("query") String query,
		@Param("model") String model,
		@Param("versionIds") List<Long> versionIds,
		@Param("maxDistance") double maxDistance);

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
