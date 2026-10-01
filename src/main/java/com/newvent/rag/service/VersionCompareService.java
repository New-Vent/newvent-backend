package com.newvent.rag.service;

import java.util.List;

/**
 * 버전 간 차이 비교. b4가 공개 API(recommend/similar) 와 함께 구현
 * ★ 구현 빈이 없어도 기동에 문제 X
 */
public interface VersionCompareService {

	/** 두 버전이 얼마나 달라졌는지. changedChunkIds는 바뀐 청크 위치 */
	record VersionDiff(double similarity, List<Long> changedChunkIds) {}

	VersionDiff compare(Long eventId, Long oldVersionId, Long newVersionId);
}
