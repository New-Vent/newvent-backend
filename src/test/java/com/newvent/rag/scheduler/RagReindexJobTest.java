package com.newvent.rag.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.rag.dto.response.ReindexAllResponse;
import com.newvent.rag.service.EmbeddingService;

/** 스케줄러는 얇게 둔다 — 서비스 호출 1회 위임만 확인한다. DB·스프링 없이 돈다. */
class RagReindexJobTest {

    static final class FakeEmbedding extends EmbeddingService {
        int calls;
        FakeEmbedding() { super(null, null, null, null, null, null); }

        @Override
        public ReindexAllResponse indexAllEvents() {
            calls++;
            return new ReindexAllResponse(2, 2, List.of(), 15);
        }
    }

    static final class BlockingEmbedding extends EmbeddingService {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        BlockingEmbedding() { super(null, null, null, null, null, null); }

        @Override
        public ReindexAllResponse indexAllEvents() {
            calls.incrementAndGet();
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ReindexAllResponse(1, 1, List.of(), 1);
        }
    }

    @Test
    @DisplayName("스케줄 실행 시 전체 재색인을 1회 호출한다")
    void delegatesOnce() {
        FakeEmbedding embedding = new FakeEmbedding();
        RagReindexJob job = new RagReindexJob(embedding);

        job.run();

        assertEquals(1, embedding.calls);
    }

    @Test
    @DisplayName("이미 돌고 있으면 두 번째 실행은 건너뛴다")
    void skipsOverlappingRun() throws Exception {
        BlockingEmbedding embedding = new BlockingEmbedding();
        RagReindexJob job = new RagReindexJob(embedding);

        Thread t = new Thread(job::run);
        t.start();
        assertTrue(embedding.entered.await(10, TimeUnit.SECONDS));
        job.run();
        embedding.release.countDown();
        t.join(10_000);

        assertEquals(1, embedding.calls.get());
    }
}
