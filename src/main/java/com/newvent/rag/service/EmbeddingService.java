package com.newvent.rag.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.generation.repository.LlmCallLogRepository;
import com.newvent.rag.domain.RagChunk;
import com.newvent.rag.dto.response.IndexStatusResponse;
import com.newvent.rag.dto.response.QualityTrendResponse;
import com.newvent.rag.dto.response.ReindexAllResponse;
import com.newvent.rag.dto.response.ReindexAllStatus;
import com.newvent.rag.repository.RagChunkRepository;
import com.newvent.rag.service.RagChunkingService.Chunk;

import lombok.extern.slf4j.Slf4j;

/**
 * 임베딩 파이프라인 진입점. 자르기·저장·현황을 맡는다.
 * 찾기는 SimilarityService 몫이다.
 */
@Slf4j
@Service
public class EmbeddingService {

	private final EventVersionRepository versions;
	private final RagChunkRepository chunks;
	private final RagChunkingService chunking;
	private final EmbeddingClient embedding;
	private final EventRepository events;
	private final LlmCallLogRepository logs;
	private final TransactionTemplate txTemplate;

	// 전체 재색인은 한 번에 하나만 돈다 — 야간 스케줄러와 관리자 버튼이 같은 자리를 쓴다
	private final AtomicBoolean reindexing = new AtomicBoolean();
	private volatile Instant reindexStartedAt;
	private volatile Instant reindexFinishedAt;
	private volatile ReindexAllResponse lastResult;

	public EmbeddingService(EventVersionRepository versions, RagChunkRepository chunks,
	        RagChunkingService chunking, EmbeddingClient embedding,
	        EventRepository events, LlmCallLogRepository logs, TransactionTemplate txTemplate) {
		this.versions = versions;
		this.chunks = chunks;
		this.chunking = chunking;
		this.embedding = embedding;
		this.events = events;
		this.logs = logs;
		this.txTemplate = txTemplate;
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
		chunks.deleteByEventIdAndVersionId(eventId, versionId);
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

	/**
	 * 전체 재색인 자리 선점. REST(요청 스레드)와 스케줄러가 같은 자리를 쓴다.
	 * @Async 는 요청 스레드를 떠나서 실행되므로 409 판정은 발사 전에 여기서 끝낸다.
	 * true 를 받으면 반드시 indexAllEventsAsync() 로 발사해야 한다 (해제는 finally).
	 */
	public boolean tryClaimReindexSlot() {
		if (!reindexing.compareAndSet(false, true)) {
			return false;
		}
		reindexStartedAt = Instant.now();
		return true;
	}

	/** 진행 상태 조회. 실행 중 여부·시작·종료·직전 결과. */
	public ReindexAllStatus getReindexAllStatus() {
		return new ReindexAllStatus(reindexing.get(), reindexStartedAt,
				reindexFinishedAt, lastResult);
	}

	/**
	 * 전체 재색인 비동기 발사. 호출 전에 tryClaimReindexSlot() 으로 자리를 잡아야 한다.
	 * @Async self-invocation 함정을 피하려고 컨트롤러·스케줄러가 프록시 경유로 직접 부른다.
	 */
	@Async
	public CompletableFuture<ReindexAllResponse> indexAllEventsAsync() {
		try {
			ReindexAllResponse result = indexAllEvents();
			lastResult = result;
			log.info("전체 재색인 완료: {}/{} 성공, 실패 {}건, 청크={}",
					result.succeeded(), result.totalEvents(),
					result.failedEvents().size(), result.totalChunks());
			return CompletableFuture.completedFuture(result);
		} catch (RuntimeException e) {
			log.error("전체 재색인 중단", e);
			throw e;
		} finally {
			reindexFinishedAt = Instant.now();
			reindexing.set(false);
		}
	}

	/**
	 * 전체 이벤트 재색인. 하나가 터져도 멈추지 않고 다음으로 넘어간다.
	 * ★ 이벤트당 트랜잭션 경계는 TransactionTemplate 으로 명시한다. this.reindex()
	 *   자기 호출은 Spring 프록시를 안 타서 @Transactional 이 무시되므로, 경계를 안 잡으면
	 *   삭제는 커밋되고 저장은 안 되는 채로 남는다 (그 버전 청크 0개).
	 * ★ 시드 데이터(SEED: ...)는 건너뛴다. 데모용 낡은 벡터가 운영 검색에 섞이는 것을 막는다.
	 */
	public ReindexAllResponse indexAllEvents() {
		List<ReindexAllResponse.FailedEvent> failed = new ArrayList<>();
		int succeeded = 0;
		int totalChunks = 0;
		List<Event> targets = events.findAllByDeletedAtIsNull();
		for (Event event : targets) {
			if (isSeedData(event.getTitle())) {
				continue;
			}
			try {
				Integer done = txTemplate.execute(status -> reindex(event.getId(), null));
				totalChunks += done != null ? done : 0;
				succeeded++;
			} catch (RuntimeException e) {
				log.error("전체 재색인 실패: eventId={}", event.getId(), e);
				failed.add(new ReindexAllResponse.FailedEvent(event.getId(), errorOf(e)));
			}
		}
		return new ReindexAllResponse(succeeded + failed.size(), succeeded, failed, totalChunks);
	}

	private static String errorOf(RuntimeException e) {
		return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
	}

	// 시드 판정. 앞뒤 공백·대소문자를 무시한다 (" seed: ", "Seed:" 도 시드다)
	static boolean isSeedData(String title) {
		return title != null && title.strip().toUpperCase(java.util.Locale.ROOT).startsWith("SEED:");
	}

	// 주간 품질 추이. 호출 건수·RAG 사용 건수·청크 수를 주별로 묶는다.
	// distance 평균은 저장하지 않으므로(쿼리 시점 계산값) 건수 기반으로 추이를 본다.
	@Transactional(readOnly = true)
	public List<QualityTrendResponse> getQualityTrend(int weeks) {
		int w = weeks <= 0 ? 4 : weeks;
		Instant from = Instant.now().minus(Duration.ofDays(w * 7L));
		Map<LocalDate, WeekAgg> byWeek = new LinkedHashMap<>();
		for (var row : logs.sumRagUsageByWeek(from)) {
			byWeek.put(row.getWeekStart(),
					new WeekAgg(row.getTotalCalls(), row.getRagUsedCalls(), 0));
		}
		for (var row : chunks.countChunksByWeek(from)) {
			WeekAgg cur = byWeek.getOrDefault(row.getWeekStart(), new WeekAgg(0, 0, 0));
			byWeek.put(row.getWeekStart(),
					new WeekAgg(cur.totalCalls(), cur.ragUsedCalls(), row.getChunkCount()));
		}
		List<QualityTrendResponse> out = new ArrayList<>(byWeek.size());
		for (Map.Entry<LocalDate, WeekAgg> e : byWeek.entrySet()) {
			WeekAgg v = e.getValue();
			out.add(new QualityTrendResponse(e.getKey(), v.totalCalls(), v.ragUsedCalls(), v.chunkCount()));
		}
		return out;
	}

	// 주별 집계 버킷. long[3] 배열 인덱스로 들고 있으면 자리 바뀔 때 컴파일이 못 잡는다
	private record WeekAgg(long totalCalls, long ragUsedCalls, long chunkCount) {}
}
