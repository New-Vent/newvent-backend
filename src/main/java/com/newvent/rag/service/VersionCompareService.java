package com.newvent.rag.service;

import java.util.List;

import com.newvent.rag.dto.response.VersionSimilarityResponse;

/**
 * 유사 버전 탐색 + 버전 간 차이 비교.
 *
 * ★ similarVersions 는 b2.5에서 구현한다. compare 는 b4(공개 API)와 함께 구현한다.
 * ★ 구현 빈이 없어도 기동에 문제 X
 */
public interface VersionCompareService {

	/**
	 * 기준 버전과 비슷한 같은 이벤트 내 다른 버전을 유사도 내림차순으로 돌려준다.
	 * 기준 버전이 없거나 타 이벤트 소속이면 404. 기준 버전이 미색인이면 빈 목록.
	 */
	List<VersionSimilarityResponse> similarVersions(Long eventId, Long versionId, int topK);

	/** 두 버전이 얼마나 달라졌는지. changedChunkIds는 바뀐 청크 위치 */
	record VersionDiff(double similarity, List<Long> changedChunkIds) {}

	VersionDiff compare(Long eventId, Long oldVersionId, Long newVersionId);
}
