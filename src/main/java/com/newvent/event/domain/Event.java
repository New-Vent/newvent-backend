package com.newvent.event.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

import jakarta.persistence.*;

import com.newvent.admin.domain.Admin;
import com.newvent.common.domain.BaseTimeEntity;
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

    @Column(name = "start_notified_at")
    private OffsetDateTime startNotifiedAt;

    @Column(name = "closing_soon_notified_at")
    private OffsetDateTime closingSoonNotifiedAt;

    @Column(name = "end_notified_at")
    private OffsetDateTime endNotifiedAt;

    public boolean deleted() {
        return deletedAt != null;
    }

    public void delete(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void restore() {
        this.deletedAt = null;
    }

    // 게시(DRAFT→PUBLISHED) 및 재게시(다른 버전으로 교체)
    public void publish(EventVersion version) {
        this.status = EventStatus.PUBLISHED;
        this.publishedVersion = version;
    }

    // 게시 내리기(PUBLISHED → DRAFT), 다시 게시해도 이미 보낸 알림은 재발송하지 않는다
    public void unpublish() {
        this.status = EventStatus.DRAFT;
        this.publishedVersion = null;
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

    // 관리자 생성 API — 항상 DRAFT
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

    /** 관리자 수정 API — 부분 수정 병합과 검증은 서비스가 끝낸 값을 받는다. */
    public void updateInfo(
            String title,
            EventTemplate template,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            MembershipGrade grade) {
        // 날짜가 실제로 바뀌면 그 날짜 기준으로 이미 보낸 알림 표시를 지운다 —
        // 안 지우면 연장된 새 날짜가 와도 "이미 보냈다"고 착각해 재발송을 안 한다.
        if (!Objects.equals(this.startDate, startDate)) {
            this.startNotifiedAt = null;
        }
        if (!Objects.equals(this.endDate, endDate)) {
            this.closingSoonNotifiedAt = null;
        }

        this.title = title;
        this.template = template;
        this.startDate = startDate;
        this.endDate = endDate;
        this.grade = grade;
    }

    /** DRAFT 는 게시 전이라 기간이 지나도 다시 잡을 수 있게 잠그지 않는다. */
    public boolean editLocked(OffsetDateTime now) {
        if (status == EventStatus.ENDED) {
            return true;
        }
        return status == EventStatus.PUBLISHED && periodEnded(now);
    }

    /**
     * 종료일시가 지났는지. 종료 시각과 같은 순간도 지난 것으로 본다.
     * 자동 종료 스케줄러는 endDate &lt; now 로 종료하므로, 그 직전 한 순간까지 포함해 수정과 게시를 함께 막는다.
     */
    public boolean periodEnded(OffsetDateTime now) {
        return endDate != null && !now.isBefore(endDate);
    }

    public boolean published() {
        return status == EventStatus.PUBLISHED;
    }

    /** 게시 중인지는 서비스가 확인한 뒤 호출한다. 게시 버전(publishedVersion)은 그대로 둔다. */
    public void end() {
        this.status = EventStatus.ENDED;
    }

    // 시작·마감임박·종료 알림 — 같은 이벤트에 같은 종류가 두 번 가지 않도록 보낸 시각을 남긴다.
    public void markStartNotified(OffsetDateTime now) {
        this.startNotifiedAt = now;
    }

    public void markClosingSoonNotified(OffsetDateTime now) {
        this.closingSoonNotifiedAt = now;
    }

    public void markEndNotified(OffsetDateTime now) {
        this.endNotifiedAt = now;
    }
}
