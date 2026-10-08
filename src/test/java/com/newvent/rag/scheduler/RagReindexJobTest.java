package com.newvent.rag.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.rag.dto.response.ReindexAllResponse;
import com.newvent.rag.service.EmbeddingService;

/**
 * 스케줄러는 얇게 둔다 — 자리 선점 1회 + 비동기 발사 1회 위임만 확인한다. DB·스프링 없이 돈다.
 * 중복 가드 본체는 서비스(tryClaimReindexSlot)에 있어서 여기서는 건너뜀 경로만 본다.
 */
class RagReindexJobTest {

    static final class FakeEmbedding extends EmbeddingService {
        int claims;
        int asyncCalls;
        boolean slotFree = true;
        FakeEmbedding() { super(null, null, null, null, null, null, null); }

        @Override
        public boolean tryClaimReindexSlot() {
            claims++;
            return slotFree;
        }

        @Override
        public CompletableFuture<ReindexAllResponse> indexAllEventsAsync() {
            asyncCalls++;
            return CompletableFuture.completedFuture(new ReindexAllResponse(2, 2, List.of(), 15));
        }
    }

    @Test
    @DisplayName("스케줄 실행 시 자리 선점 후 비동기 발사를 1회씩 호출한다")
    void delegatesOnce() {
        FakeEmbedding embedding = new FakeEmbedding();
        RagReindexJob job = new RagReindexJob(embedding);

        job.run();

        assertEquals(1, embedding.claims);
        assertEquals(1, embedding.asyncCalls);
    }

    @Test
    @DisplayName("자리가 없으면 발사는 하지 않는다")
    void skipsWhenClaimFails() {
        FakeEmbedding embedding = new FakeEmbedding();
        embedding.slotFree = false;
        RagReindexJob job = new RagReindexJob(embedding);

        job.run();

        assertEquals(1, embedding.claims);
        assertEquals(0, embedding.asyncCalls);
    }
}
