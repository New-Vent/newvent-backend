package com.newvent.event.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.*;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.newvent.generation.domain.ChatMessage;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "event_versions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_event_versions_event_version_no",
                        columnNames = {"event_id", "version_no"}
                ),
                @UniqueConstraint(
                        name = "uk_event_versions_request_message_id",
                        columnNames = "request_message_id"
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "html_content", nullable = false, columnDefinition = "text")
    private String htmlContent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "is_checkpoint", nullable = false)
    private boolean checkpoint = false;

    @Column(name = "checkpointed_at")
    private OffsetDateTime checkpointedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "event_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_event_versions_event")
    )
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "source_version_id",
            foreignKey = @ForeignKey(name = "fk_event_versions_source_version")
    )
    private EventVersion sourceVersion;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "request_message_id",
            unique = true,
            foreignKey = @ForeignKey(name = "fk_event_versions_request_message")
    )
    private ChatMessage requestMessage;

    /** 인메모리 시드용. DB 저장 전 단계에서는 event 연관 없이 HTML 만 둔다. */
    public static EventVersion htmlOnly(String htmlContent) {
        EventVersion version = new EventVersion();
        version.versionNo = 1;
        version.htmlContent = htmlContent;
        return version;
    }
}
