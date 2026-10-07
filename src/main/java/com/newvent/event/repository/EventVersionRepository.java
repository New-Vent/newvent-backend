package com.newvent.event.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.event.domain.EventVersion;

public interface EventVersionRepository extends JpaRepository<EventVersion, Long> {

    @EntityGraph(attributePaths = {"requestMessage", "sourceVersion"})
    List<EventVersion> findByEventIdOrderByVersionNoDesc(Long eventId);

    Optional<EventVersion> findByIdAndEventId(Long versionId, Long eventId);

    /**
     * 이 이벤트의 마지막 버전. version_no 최대값 하나.
     */
    Optional<EventVersion> findTopByEventIdOrderByVersionNoDesc(Long eventId);

    /**
     * 일괄 재색인용. 오래된 버전부터.
     */
    List<EventVersion> findByEventIdOrderByVersionNoAsc(Long eventId);

    long countByEventId(Long eventId);

    /**
     * 관리자 목록 한 페이지의 이벤트별 최신 version_no. 행은 [eventId(Long), versionNo(Integer)].
     * 버전이 없는 이벤트는 결과에 없다.
     */
    @Query("SELECT v.event.id, MAX(v.versionNo) FROM EventVersion v "
            + "WHERE v.event.id IN :eventIds GROUP BY v.event.id")
    List<Object[]> findLatestVersionNos(@Param("eventIds") Collection<Long> eventIds);
}
