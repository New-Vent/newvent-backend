package com.newvent.generation.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LLM 호출 1건 기록. 성공·실패·연결 실패를 가리지 않고 남김.
 */
@Entity
@Table(name = "llm_call_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmCallLog {

	// 시도 결과 분류. DB CHECK 5 값과 1:1 대응. 값 추가 시 마이그레이션도 함께
	public enum FailureType{
		VALIDATION_FAIL, TIMEOUT, LLM_ERROR, STOPPED, TRUNCATED
	}
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	// 소속 이벤트. 전 플로우는 이벤트 범위라는 전체
	@Column(name = "event_id", nullable = false)
	private Long eventId;
	
	// 연결된 버전. 결과가 저장까지 이어졌을 때 '마지막 성공 시도'에만 붙는다.
	@Column(name = "version_id")
	private Long versionId;
	
	// 1-based 시도 회차. 1 = 최초 호출
	@Column(name = "attempt_no", nullable = false)
	private int attemptNo;
	
	// 잘림 여부. done_reason == length 로 잘린 출력
	@Column(nullable = false)
	private boolean truncated;
	
	// 호출 자체의 성패. 검증 통과 여부와 다름 주의
	// (검증 실패도 "호출은 성공"이라 success = true, failureType=VALIDATION_FAIL이 될 수 있음)
	@Column(nullable = false)
	private boolean success;
	
	// 실패 분류. 성공 시 null
	@Enumerated(EnumType.STRING)
	@Column(name = "failure_type", length = 50)
	private FailureType failureType;
	
	// 시도별 상세 실패 코드들을 파이프로 이어붙인 값. RetryService Trace의 실패 목록이 저장되는 곳
	// RAG 검색 facet·주간 튜닝 리포트의 재료. 성공 시 null
	@Column(name = "fail_codes", length = 500)
	private String failCodes;
	
	// LlmClient가 잰 wall 시간 그대로 (ms). null 이면 측정 불가 건 (연결 실패 등)
	@Column(name = "response_time_ms")
	private Integer responseTimeMs;
	
	// 실제 모델명
	@Column(name = "model_name", nullable = false, length = 50)
	private String modelName;
	
	// 실제 호출을 수행한 프로바이더 (LlmClient.providerName())
	// model_name(무엇으로 돌렸나)과 분리 - Ollama -> Bedrock 전환 전후 비교용
	@Column(nullable = false, length = 20)
	private String provider;
	
	// 주간 합계 입력분. LlmClient inputTokens 매핑
	@Column(name = "input_tokens", nullable = false)
	private int inputTokens;
	
	// 주간 합계 출력분. LlmClient outputTokens 매핑. total 은 SUM 으로 계산 (컬럼으로 두지 않음)
	@Column(name = "output_tokens", nullable = false)
	private int outputTokens;
	
	// error 테이블 행 연결 (상세 원인 추적용). 없어도 되는 연결이라 nullable
	@Column(name = "error_id")
	private Long errorId;
	
	// 호출 시각. KST 경계 산정(countToday·주간집계)의 기준 컬럼. updatable=false (생성 후 불변)
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	
	private LlmCallLog(Long eventId, Long versionId, int attemptNo, String modelName,
			String provider, int inputTokens, int outputTokens, Integer responseTimeMs,
			boolean truncated, boolean success, FailureType failureType, String failCodes, Instant createdAt) {
		// 불변식 : 성공 호출에 실패 정보가 섞이면 데이터 오염이라 생성 단계에서 차단
		if (success && (failureType != null || failCodes != null)) {
			throw new IllegalArgumentException("성공 호출에 실패 정보가 있을 수 없습니다.");
		}
		
		if (!success && failureType == null) {
			throw new IllegalArgumentException("실패 호출은 failureType 이 있어야 합니다.");
		}
		
		if (truncated && failureType != FailureType.TRUNCATED) {
			throw new IllegalArgumentException("truncated=true 인데 failureType이 TRUNCATED 가 아닙니다.");
		}
		
		if (!truncated && failureType == FailureType.TRUNCATED) {
			throw new IllegalArgumentException("failureType이 TRUNCATED 인데 truncated = false 입니다.");
		}
		
		this.eventId = eventId;
		this.versionId = versionId;
		this.attemptNo = attemptNo;
		this.modelName = modelName;
		this.provider = provider;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.responseTimeMs = responseTimeMs;
		this.success = success;
		this.truncated = truncated;
		this.failureType = failureType;
		this.failCodes = failCodes;
		this.createdAt = createdAt;
	}
	
	public static LlmCallLog create(Long eventId, Long versionId, int attemptNo, String modelName,
			String provider, int inputTokens, int outputTokens, Integer responseTimeMs,
			boolean truncated, boolean success, FailureType failureType, String failCodes, Instant createdAt) {
		return new LlmCallLog(eventId, versionId, attemptNo, modelName,
				provider, inputTokens, outputTokens, responseTimeMs, 
				truncated, success, failureType, failCodes, createdAt);
	}
}
