package com.newvent.notification.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.*;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.newvent.admin.domain.Admin;
import com.newvent.event.domain.Event;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "admin_notifications")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "admin_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_admin_notifications_admin")
    )
    private Admin admin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "event_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_admin_notifications_event")
    )
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminNotificationType type;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static AdminNotification of(Admin admin, Event event, AdminNotificationType type, String message) {
        AdminNotification notification = new AdminNotification();
        notification.admin = admin;
        notification.event = event;
        notification.type = type;
        notification.message = message;
        return notification;
    }

    public boolean read() {
        return readAt != null;
    }

    public void markRead(OffsetDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
