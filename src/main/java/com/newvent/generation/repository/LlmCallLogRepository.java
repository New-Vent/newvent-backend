package com.newvent.generation.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.generation.domain.LlmCallLog;

/** 로그 조회 전담 - 쓰기는 save() 기본 제공분으로 충분해서 추가 메서드 없음
 *  - countToday() -> 아래 범위 카운트 (일일 상한 체크용, 매 호출 실행이라 인덱스 필수)
 *  - weeklyUsage() -> 아래 주간 합계 (관리자 사용량 화면용)
 */
public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long>{

	// (start, end) 구간 행 수. KST 자정 ~ 자정 범위는 서비스가 계산해서 넘김
	// 레포지토리는 타임존을 모름 ( 시간 정책은 서비스 + Clock 이 소유 )
	long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant start, Instant end);

	// 주간 토큰 합계 + 모델별 분해
	// - date_trunc('week') 는 월요일 시작이라 월~일 KST 주간과 일치
	// - AT TIME ZONE 을 빼먹으면 UTC 주간으로 어긋나니 삭제 금지
	@Query(value = """
			select		date_trunc('week', created_at at time zone 'Asia/Seoul')::date as "weekStart",
						model_name as "modelName",
						coalesce(sum(input_tokens), 0) as "inputTokens",
						coalesce(sum(output_tokens), 0) as "outputTokens"
			  from		llm_call_logs
			 where		created_at >= :from
			 group by	1, 2
			 order by	1 desc, 2
			""", nativeQuery = true)
	List<WeeklyUsageRow> sumTokensByWeek(@Param("from") Instant from);

	// 주간 합계 1행. record 가 아니라 읽기 전용 투영 (테이블 X)
	interface WeeklyUsageRow {
		LocalDate getWeekStart();
		String getModelName();
		long getInputTokens();
		long getOutputTokens();
	}

	// 관리자 목록용. null 조건은 무시한다.
	// JPQL이라 카운트 쿼리는 Spring Data 가 자동 생성한다.
	@Query("""
			select l from LlmCallLog l
			 where (:eventId is null or l.event.id = :eventId)
			   and (:callOk is null or l.callOk = :callOk)
			   and (:from is null or l.createdAt >= :from)
			   and (:to is null or l.createdAt <= :to)
			""")
	Page<LlmCallLog> search(@Param("eventId") Long eventId,
			@Param("callOk") Boolean callOk,
			@Param("from") Instant from,
			@Param("to") Instant to,
			Pageable pageable);

	// Aborted 실패 시도 복구용. Gateway가 먼저 저장한 행을 (request_id, attempt_no) UK로 찾는다
	Optional<LlmCallLog> findByRequestIdAndAttemptNo(UUID requestId, int attemptNo);
}
