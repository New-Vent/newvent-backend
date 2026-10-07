package com.newvent.rag.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.newvent.rag.dto.response.ReindexAllResponse;
import com.newvent.rag.service.EmbeddingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 야간 전체 재색인. cron 기본값은 매일 03:00 KST.
 *
 * ★ ShedLock 같은 분산 락 없이 단일 인스턴스 전제다. 다중 인스턴스로 늘리면 락이 필요하다.
 * ★ 실패해도 다음 스케줄에 다시 돈다. 알림 발송은 붙이지 않았다(로그만).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagReindexJob {

    private final EmbeddingService embeddingService;

    @Scheduled(
            cron = "${rag.reindex.cron:0 0 3 * * ?}",
            zone = "Asia/Seoul"
    )
    public void run() {
        ReindexAllResponse result = embeddingService.indexAllEvents();
        log.info("야간 전체 재색인 완료: {}/{} 성공, 실패={}, 청크={}",
                result.succeeded(), result.totalEvents(),
                result.failedEventIds(), result.totalChunks());
    }
}
