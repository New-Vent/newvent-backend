package com.newvent.notification.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.notification.domain.AdminNotification;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {

    // eventId가 없으면(null) 관리자의 전체 알림함, 있으면 그 이벤트만.
    @EntityGraph(attributePaths = "event")
    @Query(
            value = "SELECT n FROM AdminNotification n WHERE n.admin.id = :adminId "
                    + "AND (:eventId IS NULL OR n.event.id = :eventId) "
                    + "ORDER BY n.createdAt DESC, n.id DESC",
            countQuery = "SELECT COUNT(n) FROM AdminNotification n WHERE n.admin.id = :adminId "
                    + "AND (:eventId IS NULL OR n.event.id = :eventId)")
    Page<AdminNotification> findByAdminIdAndOptionalEventId(
            @Param("adminId") Long adminId, @Param("eventId") Long eventId, Pageable pageable);

    Optional<AdminNotification> findByIdAndAdminId(Long id, Long adminId);
}
