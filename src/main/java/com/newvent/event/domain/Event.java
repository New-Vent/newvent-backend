package com.newvent.event.domain;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.*;

import com.newvent.admin.domain.Admin;
import com.newvent.common.domain.BaseTimeEntity;
import com.newvent.event.support.EntityTimestamps;
import com.newvent.user.domain.MembershipGrade;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_admin_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_events_owner_admin")
    )
    private Admin ownerAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "template_id",
            foreignKey = @ForeignKey(name = "fk_events_template")
    )
    private EventTemplate template;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "start_date")
    private OffsetDateTime startDate;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus status = EventStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipGrade grade;

    @Column(columnDefinition = "text")
    private String url;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "published_version_id",
            unique = true,
            foreignKey = @ForeignKey(name = "fk_events_published_version")
    )
    private EventVersion publishedVersion;

    public boolean deleted() {
        return deletedAt != null;
    }

    public String templateCode() {
        return template == null ? null : template.getCode();
    }

    public String thumbnailPath() {
        return template == null ? null : template.getThumbnailPath();
    }

    public String completedHtml() {
        return publishedVersion == null ? null : publishedVersion.getHtmlContent();
    }

    public List<MembershipGrade> targetGrades() {
        return grade == null ? List.of() : List.of(grade);
    }

    /** 관리자 생성 API — 항상 DRAFT. */
    public static Event createDraft(
            Admin ownerAdmin,
            EventTemplate template,
            String title,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            MembershipGrade grade) {
        Event event = new Event();
        event.ownerAdmin = ownerAdmin;
        event.template = template;
        event.title = title;
        event.startDate = startDate;
        event.endDate = endDate;
        event.status = EventStatus.DRAFT;
        event.reviewStatus = ReviewStatus.PENDING;
        event.grade = grade == null ? MembershipGrade.NORMAL : grade;
        return event;
    }

    /** 인메모리 시드·테스트용. */
    public static Event reconstitute(
            Long id,
            Admin ownerAdmin,
            EventTemplate template,
            String title,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            EventStatus status,
            MembershipGrade grade,
            OffsetDateTime deletedAt,
            EventVersion publishedVersion,
            OffsetDateTime updatedAt) {
        Event event = new Event();
        event.id = id;
        event.ownerAdmin = ownerAdmin;
        event.template = template;
        event.title = title;
        event.startDate = startDate;
        event.endDate = endDate;
        event.status = status;
        event.reviewStatus = ReviewStatus.PENDING;
        event.grade = grade == null ? MembershipGrade.NORMAL : grade;
        event.deletedAt = deletedAt;
        event.publishedVersion = publishedVersion;
        EntityTimestamps.set(event, updatedAt, updatedAt);
        return event;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public void touchUpdatedAt(OffsetDateTime updatedAt) {
        OffsetDateTime createdAt = getCreatedAt() != null ? getCreatedAt() : updatedAt;
        EntityTimestamps.set(this, createdAt, updatedAt);
    }
}
