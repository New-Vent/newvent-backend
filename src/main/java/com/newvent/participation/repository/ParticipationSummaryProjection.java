package com.newvent.participation.repository;

// 참여 요약 집계 쿼리의 결과를 받는 인터페이스
public interface ParticipationSummaryProjection {

    long getTotalParticipationCount();
    long getRewardCount();
    long getPendingCount();
}
