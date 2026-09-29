package com.newvent.event.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByIdAndDeletedAtIsNull(Long eventId);

    // publishedVersion은 LAZY 연관관계이고 open-in-view: false라, 트랜잭션 안에서 fetch join으로 같이 가져온다.
    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.publishedVersion "
            + "WHERE e.id = :id AND e.deletedAt IS NULL AND e.status <> :excludedStatus")
    Optional<Event> findPublicEventById(@Param("id") Long id, @Param("excludedStatus") EventStatus excludedStatus);

    // 관리자 상세 조회용. template/publishedVersion 모두 LAZY라 fetch join으로 같이 가져온다.
    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.template LEFT JOIN FETCH e.publishedVersion "
            + "WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<Event> findAdminEventById(@Param("id") Long id);

    // 관리자 목록 조회 — 이름(부분 일치)·상태·기간(구간 겹침) 조건은 전달되지 않으면(null) 제외한다.
    // namePattern은 Service에서 이미 "%값%" 형태로 소문자 변환까지 마친 LIKE 패턴을 그대로 받는다.
    // 정렬은 기존 인메모리 저장소와 동일하게 수정일 내림차순(동률이면 id 내림차순)으로 맞춘다.
    @Query(
            value = """
            SELECT e FROM Event e
            LEFT JOIN FETCH e.template t
            WHERE e.deletedAt IS NULL
              AND (:namePattern IS NULL OR LOWER(e.title) LIKE :namePattern)
              AND (:status IS NULL OR e.status = :status)
              AND (:periodFrom IS NULL OR e.endDate >= :periodFrom)
              AND (:periodTo IS NULL OR e.startDate <= :periodTo)
            ORDER BY e.updatedAt DESC, e.id DESC
            """,
            countQuery = """
            SELECT COUNT(e) FROM Event e
            WHERE e.deletedAt IS NULL
              AND (:namePattern IS NULL OR LOWER(e.title) LIKE :namePattern)
              AND (:status IS NULL OR e.status = :status)
              AND (:periodFrom IS NULL OR e.endDate >= :periodFrom)
              AND (:periodTo IS NULL OR e.startDate <= :periodTo)
            """)
    Page<Event> findAdminEvents(
            @Param("namePattern") String namePattern,
            @Param("status") EventStatus status,
            @Param("periodFrom") OffsetDateTime periodFrom,
            @Param("periodTo") OffsetDateTime periodTo,
            Pageable pageable);

    // 카테고리(templateCode)/키워드(이벤트명)/진행상태(progress)는 전달되지 않으면(null) 조건에서 제외한다.
    // progress는 EventProgress.name() 문자열("UPCOMING"/"ONGOING"/"ENDED")을 그대로 받는다.
    // keywordPattern은 Service에서 이미 "%값%" 형태로 소문자 변환까지 마친 LIKE 패턴을 그대로 받는다.
    // 종료 이벤트 목록 노출 정책(REQ-PUB-06)이 보류 상태라, 목록은 우선 PUBLISHED만 노출한다 —
    // status는 자동으로 ENDED 로 바뀌지 않으므로 progress='ENDED' 판단은 endDate 경과 여부로만 한다.
    @Query(
            value = """
            SELECT e FROM Event e
            LEFT JOIN FETCH e.template t
            WHERE e.deletedAt IS NULL
              AND e.status = com.newvent.event.domain.EventStatus.PUBLISHED
              AND (:templateCode IS NULL OR t.code = :templateCode)
              AND (:keywordPattern IS NULL OR LOWER(e.title) LIKE :keywordPattern)
              AND (:progress IS NULL
                    OR (:progress = 'UPCOMING' AND e.startDate > :now)
                    OR (:progress = 'ONGOING' AND (e.startDate IS NULL OR e.startDate <= :now)
                            AND (e.endDate IS NULL OR e.endDate >= :now))
                    OR (:progress = 'ENDED' AND e.endDate IS NOT NULL AND e.endDate < :now))
            ORDER BY e.startDate DESC, e.id DESC
            """,
            countQuery = """
            SELECT COUNT(e) FROM Event e
            LEFT JOIN e.template t
            WHERE e.deletedAt IS NULL
              AND e.status = com.newvent.event.domain.EventStatus.PUBLISHED
              AND (:templateCode IS NULL OR t.code = :templateCode)
              AND (:keywordPattern IS NULL OR LOWER(e.title) LIKE :keywordPattern)
              AND (:progress IS NULL
                    OR (:progress = 'UPCOMING' AND e.startDate > :now)
                    OR (:progress = 'ONGOING' AND (e.startDate IS NULL OR e.startDate <= :now)
                            AND (e.endDate IS NULL OR e.endDate >= :now))
                    OR (:progress = 'ENDED' AND e.endDate IS NOT NULL AND e.endDate < :now))
            """)
    Page<Event> findPublicEvents(
            @Param("templateCode") String templateCode,
            @Param("keywordPattern") String keywordPattern,
            @Param("progress") String progress,
            @Param("now") OffsetDateTime now,
            Pageable pageable);
}
