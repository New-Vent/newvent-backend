package com.newvent.rag.service;

import java.util.List;

import com.newvent.rag.dto.response.VersionSimilarityResponse;

/**
 * 유사 버전 탐색.
 *
 * ★ 버전 간 차이 비교(compare)는 필요해지면 별도 인터페이스로 분리한다.
 *   미구현 메서드를 인터페이스에 두면 런타임 예외가 터지는 죽은 계약이 된다.
 * ★ 구현 빈이 없어도 기동에 문제 X
 */
public interface VersionCompareService {

	/**
	 * 기준 버전과 비슷한 같은 이벤트 내 다른 버전을 유사도 내림차순으로 돌려준다.
	 * 기준 버전이 없거나 타 이벤트 소속이면 404. 기준 버전이 미색인이면 빈 목록.
	 */
	List<VersionSimilarityResponse> similarVersions(Long eventId, Long versionId, int topK);
}
