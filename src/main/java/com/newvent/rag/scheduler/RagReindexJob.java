package com.newvent.rag.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.newvent.rag.service.EmbeddingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 야간 전체 재색인. cron 기본값은 매일 03:00 KST.
 *
 * ★ 중복 가드는 서비스(EmbeddingService.tryClaimReindexSlot)에 있다. cron 스케줄러는
 *   이전 실행이 끝나야 다음을 예약하므로 같은 잡끼리는 겹치지 않고, 진짜로 겹칠 수 있는
 *   상대(REST 엔드포인트)와 같은 자리를 쓴다. 여기서 AtomicBoolean 을 들고 있으면 막는 게 없다.
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
        if (!embeddingService.tryClaimReindexSlot()) {
            log.warn("야간 재색인 건너뜀 — 이전 실행이 아직 돌고 있다");
            return;
        }
        log.info("야간 전체 재색인 시작");
        // 스케줄 스레드를 잡아 두지 않고 @Async 로 넘긴다. 완료·실패 로그는 발사 쪽에서 찍는다.
        embeddingService.indexAllEventsAsync().whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("야간 전체 재색인 실패", ex);
            }
        });
    }
}
