package com.newvent.event.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.event.domain.EventTemplate;

public interface EventTemplateRepository extends JpaRepository<EventTemplate, Long>,
        JpaSpecificationExecutor<EventTemplate> {

    /** 값이 있는 검색 조건만 SQL에 넣어 PostgreSQL의 null 파라미터 타입 추론 문제를 피한다. */
    default Page<EventTemplate> findLibrary(Long adminId, String keyword, Boolean builtin,
            boolean includeInactive, Pageable pageable) {
        return findAll((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            var owner = root.join("ownerAdmin", JoinType.LEFT);
            filters.add(cb.or(cb.isTrue(root.get("builtin")), cb.equal(owner.get("id"), adminId)));
            if (!includeInactive) filters.add(cb.isTrue(root.get("active")));
            if (builtin != null) filters.add(cb.equal(root.get("builtin"), builtin));
            if (keyword != null) filters.add(cb.like(cb.lower(root.get("name")), keyword, '!'));
            return cb.and(filters.toArray(Predicate[]::new));
        }, org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id")));
    }

    @Query("SELECT t FROM EventTemplate t WHERE t.active = true ORDER BY t.id ASC")
    List<EventTemplate> findAllActive();

    @Query("SELECT t FROM EventTemplate t WHERE t.code = :templateKey")
    Optional<EventTemplate> findByKey(@Param("templateKey") String templateKey);
}
