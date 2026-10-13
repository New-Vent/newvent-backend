package com.newvent.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.event.domain.EventVersion;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.generation.repository.LlmCallLogRepository;
import com.newvent.rag.repository.RagChunkRepository;

/** 재색인 가드·빈 입력 방어 회귀 테스트. DB 없이 돈다. */
class EmbeddingServiceReindexGuardTest {

    private EmbeddingService service(EventVersionRepository versions, RagChunkRepository chunks) {
        return new EmbeddingService(versions, chunks, new RagChunkingService(),
                mock(EmbeddingClient.class), mock(EventRepository.class),
                mock(LlmCallLogRepository.class), null);
    }

    @Test
    @DisplayName("빈 HTML은 삭제 없이 0을 돌려준다 — 기존 색인을 날리지 않는다")
    void 빈입력은_삭제없음() {
        EventVersionRepository versions = mock(EventVersionRepository.class);
        RagChunkRepository chunks = mock(RagChunkRepository.class);
        when(versions.findByIdAndEventId(10L, 1L))
                .thenReturn(Optional.of(EventVersion.htmlOnly("   ")));

        int n = service(versions, chunks).index(1L, 10L);

        assertEquals(0, n);
        verify(chunks, never()).deleteByEventIdAndVersionId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("잡힌 자리는 중복으로 못 잡는다")
    void 자리중복불가() {
        EmbeddingService service = new EmbeddingService(null, null, null, null, null, null, null);

        assertTrue(service.tryClaimReindexSlot());
        assertFalse(service.tryClaimReindexSlot());
    }

    @Test
    @DisplayName("해제하면 다시 잡힌다 — 제출 거부 시 플래그 누수 방지")
    void 해제후_재선점() {
        EmbeddingService service = new EmbeddingService(null, null, null, null, null, null, null);

        assertTrue(service.tryClaimReindexSlot());
        assertTrue(service.isReindexAllRunning());
        service.releaseReindexSlot();
        assertFalse(service.isReindexAllRunning());
        assertTrue(service.tryClaimReindexSlot());
    }
}
