package com.newvent.generation.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "llm_call_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_llm_call_logs_request_attempt",
                columnNames = {"request_id", "attempt_no"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "event_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_llm_call_logs_event")
    )
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "version_id",
            foreignKey = @ForeignKey(name = "fk_llm_call_logs_version")
    )
    private EventVersion version;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "call_ok", nullable = false)
    private boolean callOk;

    @Column(name = "valid_ok", nullable = false)
    private boolean validOk;

    @Column(name = "failure_type", length = 50)
    @Enumerated(EnumType.STRING)
    private FailureType failureType;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @Column(name = "fail_codes", length = 500)
    private String failCodes;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(nullable = false)
    private boolean truncated = false;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private LlmCallLog(Event event, EventVersion version, UUID requestId, int attemptNo,
            String modelName, String provider, Integer inputTokens, Integer outputTokens,
            Integer responseTimeMs, boolean truncated, boolean callOk, boolean validOk,
            FailureType failureType, String failureMessage, String failCodes, Instant createdAt) {
        // 불변식 0: event_id NOT NULL (V1 FK)
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }
        // 불변식 1 (ck_failure_fields 정방향): valid면 실패 정보 금지
        if (validOk && (failureType != null || failureMessage != null || failCodes != null)) {
            throw new IllegalArgumentException("성공 호출에 실패 정보가 있을 수 없습니다.");
        }
        // 불변식 2 (ck_failure_fields 역방향): invalid면 분류 필수
        if (!validOk && failureType == null) {
            throw new IllegalArgumentException("실패 호출은 failureType 이 있어야 합니다.");
        }
        // 불변식 3 (ck_valid_requires_call): valid면 call 필수
        if (validOk && !callOk) {
            throw new IllegalArgumentException("검증 통과인데 호출 실패일 수 없습니다.");
        }
        // 불변식 4 (ck_truncated_state + 동치): 잘림은 call 성공+valid 실패일 때만, 그때는 반드시 TRUNCATED
        if (truncated && !(callOk && !validOk && failureType == FailureType.TRUNCATED)) {
            throw new IllegalArgumentException("truncated 판정과 상태가 어긋났습니다.");
        }
        if (!truncated && failureType == FailureType.TRUNCATED) {
            throw new IllegalArgumentException("failureType이 TRUNCATED인데 truncated=false 입니다.");
        }
        this.event = event;
        this.version = version;
        this.requestId = requestId;
        this.attemptNo = attemptNo;
        this.modelName = modelName;
        this.provider = provider;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.responseTimeMs = responseTimeMs;
        this.truncated = truncated;
        this.callOk = callOk;
        this.validOk = validOk;
        this.failureType = failureType;
        this.failureMessage = failureMessage;
        this.failCodes = failCodes;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static LlmCallLog create(Event event, EventVersion version, UUID requestId, int attemptNo,
            String modelName, String provider, Integer inputTokens, Integer outputTokens,
            Integer responseTimeMs, boolean truncated, boolean callOk, boolean validOk,
            FailureType failureType, String failureMessage, String failCodes, Instant createdAt) {
        return new LlmCallLog(event, version, requestId, attemptNo, modelName, provider,
                inputTokens, outputTokens, responseTimeMs, truncated, callOk, validOk,
                failureType, failureMessage, failCodes, createdAt);
    }
}
