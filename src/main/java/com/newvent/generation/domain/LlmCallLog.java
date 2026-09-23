package com.newvent.generation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
@EntityListeners(AuditingEntityListener.class)
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

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
