package com.newvent.rag.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean running = new AtomicBoolean();

    @Scheduled(
            cron = "${rag.reindex.cron:0 0 3 * * ?}",
            zone = "Asia/Seoul"
    )
    public void run() {
        // ★ 단일 인스턴스 전제라 JVM 내 가드로 충분하다. 스케줄러 풀이 4개라
        //   장시간 작업이 겹치면 같은 잡이 동시에 돌 수 있다. 분산 환경이면 DB 락 필요.
        if (!running.compareAndSet(false, true)) {
            log.warn("야간 재색인 건너뜀 — 이전 실행이 아직 돌고 있다");
            return;
        }
        try {
            ReindexAllResponse result = embeddingService.indexAllEvents();
            log.info("야간 전체 재색인 완료: {}/{} 성공, 실패={}, 청크={}",
                    result.succeeded(), result.totalEvents(),
                    result.failedEvents(), result.totalChunks());
        } finally {
            running.set(false);
        }
    }
}
