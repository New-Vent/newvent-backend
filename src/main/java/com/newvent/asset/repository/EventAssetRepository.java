package com.newvent.asset.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.asset.domain.EventAsset;

public interface EventAssetRepository extends JpaRepository<EventAsset, String> {

    List<EventAsset> findByEventIdOrderByCreatedAtAsc(Long eventId);

    Optional<EventAsset> findByIdAndEventId(String id, Long eventId);

    long countByEventId(Long eventId);
}
