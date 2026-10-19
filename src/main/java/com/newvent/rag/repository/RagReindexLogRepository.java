package com.newvent.rag.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.rag.domain.RagReindexLog;

/**
 * 재색인 기록. 추가 전용 표 — save 와 목록 읽기만 한다.
 * 기록은 EmbeddingService.recordReindex 가 창구 하나로 통일한다.
 */
public interface RagReindexLogRepository extends JpaRepository<RagReindexLog, Long> {

	// 이력 카드가 읽는 최신순 목록. page·size 는 컨트롤러에서 넣는다
	Page<RagReindexLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
