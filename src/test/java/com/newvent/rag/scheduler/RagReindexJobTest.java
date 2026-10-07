package com.newvent.rag.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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

    @Test
    @DisplayName("스케줄 실행 시 전체 재색인을 1회 호출한다")
    void delegatesOnce() {
        FakeEmbedding embedding = new FakeEmbedding();
        RagReindexJob job = new RagReindexJob(embedding);

        job.run();

        assertEquals(1, embedding.calls);
    }
}
