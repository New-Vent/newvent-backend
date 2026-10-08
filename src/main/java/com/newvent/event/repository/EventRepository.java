package com.newvent.event.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventProgress;
import com.newvent.event.domain.EventStatus;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByIdAndDeletedAtIsNull(Long eventId);

    /** 시드 멱등 가드 — SEED: 이벤트가 이미 있으면 재실행 시 건너뛴다. */
    boolean existsByTitleStartingWith(String prefix);

    /**
     * 버전을 덧붙이기 전에 이벤트 행을 잠근다 — {@code SELECT … FOR UPDATE}.
     *
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e.id FROM Event e WHERE e.id = :eventId")
    Optional<Long> lockForVersionAppend(@Param("eventId") Long eventId);

    Optional<Event> findByIdAndDeletedAtIsNotNull(Long eventId);

    // publishedVersion은 LAZY 연관관계이고 open-in-view: false라, 트랜잭션 안에서 fetch join으로 같이 가져온다.
    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.publishedVersion "
            + "WHERE e.id = :id AND e.deletedAt IS NULL AND e.status <> :excludedStatus")
    Optional<Event> findPublicEventById(@Param("id") Long id, @Param("excludedStatus") EventStatus excludedStatus);

    // 관리자 상세 조회용. template/publishedVersion 모두 LAZY라 fetch join으로 같이 가져온다.
    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.template LEFT JOIN FETCH e.publishedVersion "
            + "WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<Event> findAdminEventById(@Param("id") Long id);

    // 관리자 목록 조회 — 이름(부분 일치)·ID·상태·진행상태·기간(구간 겹침) 조건은 전달되지 않으면(null) 제외한다.
    // namePattern은 Service에서 이미 "%값%" 형태로 소문자 변환까지 마친 LIKE 패턴을 그대로 받는다.
    // idMatch 가 있으면 이름이 맞거나 ID 가 같은 이벤트를 찾는다 (검색어가 숫자일 때).
    // progress 는 공개 목록과 같은 기준으로 now 와 기간을 비교한다 — 상태(status)와는 따로 본다.
    // 정렬은 기존 인메모리 저장소와 동일하게 수정일 내림차순(동률이면 id 내림차순)으로 맞춘다.
    default Page<Event> findAdminEvents(String namePattern, Long idMatch, EventStatus status,
                                        EventProgress progress, OffsetDateTime now,
                                        OffsetDateTime periodFrom, OffsetDateTime periodTo,
                                        Pageable pageable) {
        return searchAdminEvents(namePattern, idMatch == null, idMatch, status,
                progress == null ? null : progress.name(), now,
                periodFrom == null, periodFrom, periodTo == null, periodTo, pageable);
    }

    // ★ 기간·ID 는 "있나" 를 따로 받는다 — (:periodFrom IS NULL OR …) 로 쓰면 PostgreSQL 이
    //   $n IS NULL 의 타입을 못 정해 값이 있든 없든 항상 실패했다 (could not determine data type of parameter).
    //   값은 컬럼과 비교하는 자리에만 두어 타입을 컬럼에서 정하게 한다.
    //   조건이 없으면 TRUE OR (…) 라 날짜가 비어 있는 이벤트도 그대로 나온다 — 예전 뜻과 같다.
    @Query(
            value = """
            SELECT e FROM Event e
            LEFT JOIN FETCH e.template t
            LEFT JOIN FETCH e.publishedVersion
            WHERE e.deletedAt IS NULL
              AND (:namePattern IS NULL OR LOWER(e.title) LIKE :namePattern
                    OR (:noId = false AND e.id = :idMatch))
              AND (:status IS NULL OR e.status = :status)
              AND (:progress IS NULL
                    OR (:progress = 'UPCOMING' AND e.startDate > :now)
                    OR (:progress = 'ONGOING' AND (e.startDate IS NULL OR e.startDate <= :now)
                            AND (e.endDate IS NULL OR e.endDate >= :now))
                    OR (:progress = 'ENDED' AND e.endDate IS NOT NULL AND e.endDate < :now))
              AND (:noFrom = true OR e.endDate >= :periodFrom)
              AND (:noTo = true OR e.startDate <= :periodTo)
            ORDER BY e.updatedAt DESC, e.id DESC
            """,
            countQuery = """
            SELECT COUNT(e) FROM Event e
            WHERE e.deletedAt IS NULL
              AND (:namePattern IS NULL OR LOWER(e.title) LIKE :namePattern
                    OR (:noId = false AND e.id = :idMatch))
              AND (:status IS NULL OR e.status = :status)
              AND (:progress IS NULL
                    OR (:progress = 'UPCOMING' AND e.startDate > :now)
                    OR (:progress = 'ONGOING' AND (e.startDate IS NULL OR e.startDate <= :now)
                            AND (e.endDate IS NULL OR e.endDate >= :now))
                    OR (:progress = 'ENDED' AND e.endDate IS NOT NULL AND e.endDate < :now))
              AND (:noFrom = true OR e.endDate >= :periodFrom)
              AND (:noTo = true OR e.startDate <= :periodTo)
            """)
    Page<Event> searchAdminEvents(
            @Param("namePattern") String namePattern,
            @Param("noId") boolean noId,
            @Param("idMatch") Long idMatch,
            @Param("status") EventStatus status,
            @Param("progress") String progress,
            @Param("now") OffsetDateTime now,
            @Param("noFrom") boolean noFrom,
            @Param("periodFrom") OffsetDateTime periodFrom,
            @Param("noTo") boolean noTo,
            @Param("periodTo") OffsetDateTime periodTo,
            Pageable pageable);

    // 휴지통 목록 - 최근에 삭제된 순서로 보여준다(동률이면 id 내림차순)
    @Query(
            value = "SELECT e FROM Event e LEFT JOIN FETCH e.template LEFT JOIN FETCH e.publishedVersion "
                    + "WHERE e.deletedAt IS NOT NULL "
                    + "ORDER BY e.deletedAt DESC, e.id DESC",
            countQuery = "SELECT COUNT(e) FROM Event e WHERE e.deletedAt IS NOT NULL")
    Page<Event> findDeletedEvents(Pageable pageable);

    // 카테고리(templateCode)/키워드(이벤트명)/진행상태(progress)는 전달되지 않으면(null) 조건에서 제외한다.
    // progress는 EventProgress.name() 문자열("UPCOMING"/"ONGOING"/"ENDED")을 그대로 받는다.
    // keywordPattern은 Service에서 이미 "%값%" 형태로 소문자 변환까지 마친 LIKE 패턴을 그대로 받는다.
    // 종료 이벤트 목록 노출 정책(REQ-PUB-06)이 보류 상태라, 목록은 우선 PUBLISHED만 노출한다 —
    // 자동 종료 스케줄러가 ENDED로 변경한 이벤트는 목록에서 제외된다.
    // 스케줄러 실행 전에도 progress는 현재 시각과 이벤트 기간을 비교해 판단한다.
    @Query(
            value = """
            SELECT e FROM Event e
            LEFT JOIN FETCH e.template t
            LEFT JOIN FETCH e.publishedVersion
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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Event e
        SET e.status = :endedStatus,
            e.updatedAt = :now
        WHERE e.status = :publishedStatus
          AND e.deletedAt IS NULL
          AND e.endDate IS NOT NULL
          AND e.endDate < :now
        """)
    int endExpiredEvents(
            @Param("publishedStatus") EventStatus publishedStatus,
            @Param("endedStatus") EventStatus endedStatus,
            @Param("now") OffsetDateTime now);

    // 이미 알림 보낸 건 각 notifiedAt 컬럼으로 걸러 중복 발송을 막는다.

    // endDate > now를 같이 본다 — EventExpirationJob(자동 종료)과 별도 스케줄러라 서버 재시작 등으로 타이밍이 어긋나면 종료일이 이미 지났는데도 아직 ENDED로
    // 안 바뀐 PUBLISHED 이벤트가 섞여 들어올 수 있다. 그런 이벤트까지 "시작" 알림을 보내지 않도록 막는다.
    @Query("SELECT e FROM Event e JOIN FETCH e.ownerAdmin "
            + "WHERE e.status = :published AND e.deletedAt IS NULL "
            + "AND e.startDate IS NOT NULL AND e.startDate <= :now AND e.startNotifiedAt IS NULL "
            + "AND (e.endDate IS NULL OR e.endDate > :now)")
    List<Event> findStartingEvents(@Param("published") EventStatus published, @Param("now") OffsetDateTime now);

    @Query("SELECT e FROM Event e JOIN FETCH e.ownerAdmin "
            + "WHERE e.status = :published AND e.deletedAt IS NULL "
            + "AND e.endDate IS NOT NULL AND e.endDate > :now AND e.endDate <= :threshold "
            + "AND e.closingSoonNotifiedAt IS NULL")
    List<Event> findClosingSoonEvents(
            @Param("published") EventStatus published,
            @Param("now") OffsetDateTime now,
            @Param("threshold") OffsetDateTime threshold);

    @Query("SELECT e FROM Event e JOIN FETCH e.ownerAdmin "
            + "WHERE e.status = :ended AND e.deletedAt IS NULL AND e.endNotifiedAt IS NULL")
    List<Event> findEndedEventsNeedingNotification(@Param("ended") EventStatus ended);

    // 관리자 목록 화면 상단 카운트 — 상태별로 묶어서 한 번에 센다(삭제된 건 제외).
    @Query("SELECT e.status, COUNT(e) FROM Event e WHERE e.deletedAt IS NULL GROUP BY e.status")
    List<Object[]> countGroupedByStatus();
}
