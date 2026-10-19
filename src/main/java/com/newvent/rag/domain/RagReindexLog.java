package com.newvent.rag.domain;

import java.time.Instant;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재색인 기록. V24__create_rag_reindex_log.sql 매핑
 *
 * ★ 추가 전용 log 표다. set·update 메서드를 만들지 않는다 — 기록이 바뀌면 그건 로그가 아니다.
 * ★ scope 는 SINGLE(버전 하나) / EVENT(이벤트 전체 버전) / ALL(전체 재색인).
 *   기록 창구가 2곳뿐이라(reindex() 수동 · indexAllEventsAsync()) 범위도 이 세 가지로 고정된다.
 * ★ 실패한 실행은 chunk_count 를 모른다. 트랜잭션이 통째로 없던 일이 되기 때문 — null 로 남긴다.
 */
@Getter
@Entity
@Table(name = "rag_reindex_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RagReindexLog {

	/** 범위 값들은 이 표의 CHECK 제약과 같다. 팩터리에서 먼저 걸러 둔다 */
	private static final Set<String> SCOPES = Set.of("SINGLE", "EVENT", "ALL");
	private static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 16)
	private String scope;

	@Column(name = "event_id")
	private Long eventId;

	@Column(name = "version_id")
	private Long versionId;

	/** 누가 시켰나. 관리자 ID 문자열, 자동 실행(스케줄러)은 system */
	@Column(name = "requested_by", length = 64)
	private String requestedBy;

	/** 이번 실행으로 (다시) 저장된 청크 수. 실패면 null */
	@Column(name = "chunk_count")
	private Integer chunkCount;

	@Column(name = "duration_ms")
	private Integer durationMs;

	@Column(nullable = false, length = 16)
	private String status;

	/** 실패 사유. 부분 실패(전체 재색인에서 몇 이벤트가 넘어감)는 status SUCCESS 로 남기고 사유를 여기 적는다 */
	@Column(name = "failure_message", columnDefinition = "text")
	private String failureMessage;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	private RagReindexLog(String scope, Long eventId, Long versionId, String requestedBy,
			Integer chunkCount, Integer durationMs, String status, String failureMessage) {
		if (!SCOPES.contains(scope)) {
			throw new IllegalArgumentException("scope 는 SINGLE/EVENT/ALL 중 하나여야 합니다.");
		}
		if (!STATUSES.contains(status)) {
			throw new IllegalArgumentException("status 는 SUCCESS/FAILED 중 하나여야 합니다.");
		}
		// 범위·대상 짝 맞춤 — SINGLE 은 버전까지, EVENT 는 이벤트까지, ALL 은 둘 다 없다
		if ("SINGLE".equals(scope) && (eventId == null || versionId == null)) {
			throw new IllegalArgumentException("SINGLE 기록에는 event 와 version 이 필요합니다.");
		}
		if ("EVENT".equals(scope) && eventId == null) {
			throw new IllegalArgumentException("EVENT 기록에는 event 가 필요합니다.");
		}
		if ("ALL".equals(scope) && (eventId != null || versionId != null)) {
			throw new IllegalArgumentException("ALL 기록은 대상을 두지 않습니다.");
		}
		if ("FAILED".equals(status) && (failureMessage == null || failureMessage.isBlank())) {
			throw new IllegalArgumentException("실패 기록에는 사유가 필요합니다.");
		}
		this.scope = scope;
		this.eventId = eventId;
		this.versionId = versionId;
		this.requestedBy = requestedBy;
		this.chunkCount = chunkCount;
		this.durationMs = durationMs;
		this.status = status;
		this.failureMessage = failureMessage;
		this.createdAt = Instant.now();
	}

	/** 성공·실패를 같은 팩터리로 만든다. FAILED 면 사유가 반드시 있어야 한다 (위 가드) */
	public static RagReindexLog log(String scope, Long eventId, Long versionId, String requestedBy,
			Integer chunkCount, Integer durationMs, boolean success, String failureMessage) {
		return new RagReindexLog(scope, eventId, versionId, requestedBy, chunkCount, durationMs,
				success ? "SUCCESS" : "FAILED", failureMessage);
	}
}
