package com.newvent.event.domain;

import com.newvent.admin.domain.Admin;
import com.newvent.common.domain.BaseTimeEntity;
import com.newvent.user.domain.MembershipGrade;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

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
}
