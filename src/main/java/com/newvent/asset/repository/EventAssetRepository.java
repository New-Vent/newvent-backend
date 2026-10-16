package com.newvent.asset.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.asset.domain.EventAsset;

public interface EventAssetRepository extends JpaRepository<EventAsset, String> {

    /** ★ 번호 순이다. 올린 순서가 아니다 — 화면 · 프롬프트 · 수정이 모두 이 순서를 본다 */
    List<EventAsset> findByEventIdOrderBySeqAsc(Long eventId);

    Optional<EventAsset> findByIdAndEventId(String id, Long eventId);

    long countByEventId(Long eventId);

    /**
     * 다음에 줄 번호. 비어 있으면 0 이라 첫 장이 1번이 된다.
     *
     * ★ max + 1 이다. count + 1 이 아니다 — 3장 중 2번을 지우면 count 는 2 라
     *   새 장이 3번이 되어 **살아 있는 3번과 부딪친다**(unique index 가 막는다).
     * ★ 동시에 두 장을 올리면 같은 번호가 나올 수 있다. 그때는 unique index 가 막고
     *   한쪽이 실패한다 — 번호가 겹친 채 저장되는 것보다 낫다. 관리자는 혼자 쓴다.
     */
    @Query("select coalesce(max(a.seq), 0) from EventAsset a where a.eventId = :eventId")
    int maxSeqOf(@Param("eventId") Long eventId);
}
