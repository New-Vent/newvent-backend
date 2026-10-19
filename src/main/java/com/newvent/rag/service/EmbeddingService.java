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
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.generation.repository.LlmCallLogRepository;
import com.newvent.rag.domain.RagChunk;
import com.newvent.rag.domain.RagReindexLog;
import com.newvent.rag.dto.response.IndexStatusResponse;
import com.newvent.rag.dto.response.QualityTrendResponse;
import com.newvent.rag.dto.response.ReindexAllResponse;
import com.newvent.rag.dto.response.ReindexAllStatus;
import com.newvent.rag.dto.response.ReindexLogSummaryResponse;
import com.newvent.rag.repository.RagChunkRepository;
import com.newvent.rag.repository.RagReindexLogRepository;
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
	private final RagReindexLogRepository reindexLogs;

	// 재색인 기록 한 줄은 본 트랜잭션과 별도로 남긴다 — 본 실행이 롤백돼도 FAILED 행은 살아 있어야 하므로
	private final TransactionTemplate reindexLogTx;

	// 전체 재색인은 한 번에 하나만 돈다 — 야간 스케줄러와 관리자 버튼이 같은 자리를 쓴다
	private final AtomicBoolean reindexing = new AtomicBoolean();
	private volatile Instant reindexStartedAt;
	private volatile Instant reindexFinishedAt;
	private volatile ReindexAllResponse lastResult;
	private volatile Long reindexRequestedBy;

	public EmbeddingService(EventVersionRepository versions, RagChunkRepository chunks,
	        RagChunkingService chunking, EmbeddingClient embedding,
	        EventRepository events, LlmCallLogRepository logs, TransactionTemplate txTemplate,
	        RagReindexLogRepository reindexLogs) {
		this.versions = versions;
		this.chunks = chunks;
		this.chunking = chunking;
		this.embedding = embedding;
		this.events = events;
		this.logs = logs;
		this.txTemplate = txTemplate;
		this.reindexLogs = reindexLogs;
		// 기록 템플릿은 본 트랜잭션과 분리. 테스트 스텁처럼 txTemplate 이 없으면 기록도 안 쓰므로 놔둔다
		this.reindexLogTx = txTemplate == null ? null
				: new TransactionTemplate(txTemplate.getTransactionManager());
		if (this.reindexLogTx != null) {
			this.reindexLogTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		}
	}

	/**
	 * 수동 재색인. versionId 가 없으면 그 이벤트 전체 버전을 돌린다.
	 * 컨트롤러가 부르는 창구는 이것 하나로 통일. 관리자 재색인은 실행마다 rag_reindex_log 에 한 줄 남긴다.
	 *
	 * ★ 전체 재색인(indexAllEvents)이 내부에서 이 메서드를 자기호출한다. 그때는 reindexing 플래그가
	 *   이미 잡혀 있으므로 개별 기록을 하지 않는다 — 이벤트 수만큼 기록하면 이력이 도배되고,
	 *   전체 실행은 ALL 행 하나로 대신 남긴다.
	 */
	@Transactional
	public int reindex(Long eventId, Long versionId, Long adminId) {
		if (reindexing.get()) {
			return runReindex(eventId, versionId);
		}
		long startMs = System.currentTimeMillis();
		try {
			int total = runReindex(eventId, versionId);
			recordReindex(versionId != null ? "SINGLE" : "EVENT", eventId, versionId, adminId,
					total, System.currentTimeMillis() - startMs, true, null);
			return total;
		} catch (RuntimeException e) {
			// 저장·삭제가 통째로 없던 일이 되므로 청크 수는 알 수 없다 — null 로 남긴다
			recordReindex(versionId != null ? "SINGLE" : "EVENT", eventId, versionId, adminId,
					null, System.currentTimeMillis() - startMs, false, errorOf(e));
			throw e;
		}
	}

	/**
	 * 기록되지 않는 재색인 통로. 시드 재적재 같은 자동·대량 작업용 —
	 * 시드 40종을 돌릴 때마다 이력이 40줄 늘어나는 것을 막는다.
	 */
	@Transactional
	public int reindexUnlogged(Long eventId, Long versionId) {
		return runReindex(eventId, versionId);
	}

	// 재색인 본문. reindex()·reindexUnlogged() 가 공유한다. 트랜잭션은 각자 공개 메서드 경계에 둔다
	private int runReindex(Long eventId, Long versionId) {
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

	/**
	 * 재색인 기록 한 줄. 기록이 재색인 자체를 막으면 안 되므로 저장 실패는 삼키고 본 흐름을 살린다.
	 * REQUIRES_NEW 로 본 트랜잭션과 분리 — 본문이 롤백돼도 FAILED 행이 남는다.
	 */
	private void recordReindex(String scope, Long eventId, Long versionId, Long requestedBy,
			Integer chunkCount, long durationMs, boolean success, String failureMessage) {
		try {
			reindexLogTx.executeWithoutResult(status -> reindexLogs.save(RagReindexLog.log(
					scope, eventId, versionId,
					requestedBy != null ? String.valueOf(requestedBy) : "system",
					chunkCount, (int)Math.min(Integer.MAX_VALUE, durationMs),
					success, failureMessage)));
		} catch (RuntimeException e) {
			log.warn("재색인 기록 실패: scope={} eventId={}", scope, eventId, e);
		}
	}

	// 한 버전 색인하고 저장한 개수를 돌려준다. 다른 이벤트의 버전이면 404
	@Transactional
	public int index(Long eventId, Long versionId) {
		EventVersion version = versions.findByIdAndEventId(versionId, eventId)
				.orElseThrow(() -> new EventException(EventErrorCode.VERSION_NOT_FOUND));
		List<Chunk> parts = chunking.chunk(version.getHtmlContent());
		// 청크가 하나도 안 나오면 삭제를 하지 않는다. 빈 입력으로 기존 색인을 날리지 않기 위해.
		if (parts.isEmpty()) {
			return 0;
		}
		// 지우기와 저장을 한 트랜잭션으로 묶음. 중간에 실패하면 둘 다 없던 일이 되어 절반만 저장되는 일은 없음.
		chunks.deleteByEventIdAndVersionId(eventId, versionId);
		// derived delete 는 행을 읽어 지우기로 표시만 하고 SQL 은 flush 때 나간다.
		// 같은 키를 다시 넣기 전에 flush 로 삭제를 먼저 반영한다. 안 그러면 flush 때
		// INSERT 가 DELETE 보다 먼저 나가 유니크 제약을 어긴다.
		chunks.flush();
		List<RagChunk> entities = new ArrayList<>(parts.size());
		for(Chunk p : parts) {
			float[] vec = embedding.embedDocument(p.content());
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

	// 재색인 이력 목록 — 최신순 페이징. 추가 전용 표라 읽기만 한다
	@Transactional(readOnly = true)
	public PageResponse<ReindexLogSummaryResponse> getReindexLogs(int page, int size) {
		Page<RagReindexLog> rows = reindexLogs
				.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(Math.max(size, 1), 50)));
		return PageResponse.of(rows.map(ReindexLogSummaryResponse::from).getContent(),
				page, size, rows.getTotalElements());
	}

	/**
	 * 전체 재색인 자리 선점. REST(요청 스레드)와 스케줄러가 같은 자리를 쓴다.
	 * @Async 는 요청 스레드를 떠나서 실행되므로 409 판정은 발사 전에 여기서 끝낸다.
	 * true 를 받으면 반드시 indexAllEventsAsync() 로 발사해야 한다 (해제는 finally).
	 * @param requestedBy 시킨 관리자 ID. 스케줄러는 null — 이력에 system 으로 남는다
	 */
	public boolean tryClaimReindexSlot(Long requestedBy) {
		if (!reindexing.compareAndSet(false, true)) {
			return false;
		}
		reindexRequestedBy = requestedBy;
		reindexStartedAt = Instant.now();
		return true;
	}

	/** 진행 상태 조회. 실행 중 여부·시작·종료·직전 결과. */
	public ReindexAllStatus getReindexAllStatus() {
		return new ReindexAllStatus(reindexing.get(), reindexStartedAt,
				reindexFinishedAt, lastResult);
	}

	/** 전체 재색인이 돌고 있는지 읽기만 한다. 자리를 잡지 않으므로 단건 재색인 충돌 검사에 쓴다. */
	public boolean isReindexAllRunning() {
		return reindexing.get();
	}

	/** 자리 해제. async 제출이 거부돼 finally 가 안 돌 때 호출한다. */
	public void releaseReindexSlot() {
		reindexing.set(false);
		reindexRequestedBy = null;
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
			recordReindexAll(result);
			return CompletableFuture.completedFuture(result);
		} catch (RuntimeException e) {
			log.error("전체 재색인 중단", e);
			recordReindexAllFailure(errorOf(e));
			throw e;
		} finally {
			reindexFinishedAt = Instant.now();
			reindexing.set(false);
			reindexRequestedBy = null;
		}
	}

	// 전체 재색인 1행. 판정 기준은 "실행이 끝까지 돌았나"라서 부분 실패는 SUCCESS 에 사유만 남긴다
	private void recordReindexAll(ReindexAllResponse result) {
		String partial = null;
		if (!result.failedEvents().isEmpty()) {
			String ids = result.failedEvents().stream().map(f -> String.valueOf(f.eventId()))
					.limit(5).collect(Collectors.joining(", "));
			partial = "부분 실패 " + result.failedEvents().size() + "건 (eventId " + ids
					+ (result.failedEvents().size() > 5 ? " 외" : "") + ")";
		}
		recordReindex("ALL", null, null, reindexRequestedBy, result.totalChunks(),
				elapsedMs(), true, partial);
	}

	private void recordReindexAllFailure(String message) {
		recordReindex("ALL", null, null, reindexRequestedBy, null, elapsedMs(), false, message);
	}

	private long elapsedMs() {
		return reindexStartedAt == null ? 0
				: Duration.between(reindexStartedAt, Instant.now()).toMillis();
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
				Integer done = txTemplate.execute(status -> reindex(event.getId(), null, null));
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
		// 집계원이 둘이라 청크만 있는 주가 뒤에 붙는다. 차트가 지그재그가 되지 않게 날짜순으로 정렬.
		out.sort(java.util.Comparator.comparing(QualityTrendResponse::weekStart));
		return out;
	}

	// 주별 집계 버킷. long[3] 배열 인덱스로 들고 있으면 자리 바뀔 때 컴파일이 못 잡는다
	private record WeekAgg(long totalCalls, long ragUsedCalls, long chunkCount) {}
}
