package com.newvent.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.newvent.event.domain.EventVersion;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.generation.repository.LlmCallLogRepository;
import com.newvent.rag.exception.RagException;
import com.newvent.rag.repository.RagChunkRepository;

/** 재색인 가드·빈 입력 방어 회귀 테스트. DB 없이 돈다. */
class EmbeddingServiceReindexGuardTest {

    private EmbeddingService service(EventVersionRepository versions, RagChunkRepository chunks) {
        return new EmbeddingService(versions, chunks, new RagChunkingService(),
                mock(EmbeddingClient.class), mock(EventRepository.class),
                mock(LlmCallLogRepository.class), null, null);
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
        EmbeddingService service = new EmbeddingService(null, null, null, null, null, null, null, null);

        assertTrue(service.tryClaimReindexSlot(null));
        assertFalse(service.tryClaimReindexSlot(null));
    }

    @Test
    @DisplayName("해제하면 다시 잡힌다 — 제출 거부 시 플래그 누수 방지")
    void 해제후_재선점() {
        EmbeddingService service = new EmbeddingService(null, null, null, null, null, null, null, null);

        assertTrue(service.tryClaimReindexSlot(null));
        assertTrue(service.isReindexAllRunning());
        service.releaseReindexSlot();
        assertFalse(service.isReindexAllRunning());
        assertTrue(service.tryClaimReindexSlot(null));
    }

    private EmbeddingService bare() {
        return new EmbeddingService(null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("같은 버전은 두 번 못 잡는다 — 다른 버전·이벤트는 가능")
    void 버전자리_중복불가() {
        EmbeddingService service = bare();

        assertTrue(service.bindReindexGuard(1L, 2L));
        assertFalse(service.bindReindexGuard(1L, 2L));
        assertTrue(service.bindReindexGuard(1L, 3L));
        // 이벤트 전체 자리는 그 이벤트의 진행 중 버전 하나만으로도 겹친다
        assertFalse(service.bindReindexGuard(1L, null));
        assertTrue(service.bindReindexGuard(2L, 2L));
        service.unbindReindexGuard(1L, 2L);
        assertTrue(service.bindReindexGuard(1L, 2L));
    }

    @Test
    @DisplayName("단건이 진행 중이면 전체 자리가 안 잡히고, 전체가 진행 중이면 단건 자리가 안 잡힌다")
    void 단건과_전체_상호배제() {
        EmbeddingService service = bare();

        assertTrue(service.bindReindexGuard(1L, 2L));
        // 단건 도중에 전체 재색인 시작 → 거부 (NEWVENT-BACKEND-7 경합 차단)
        assertFalse(service.tryClaimReindexSlot(null));
        service.unbindReindexGuard(1L, 2L);

        assertTrue(service.tryClaimReindexSlot(null));
        // 전체 도중에 단건 선점 → 거부
        assertFalse(service.bindReindexGuard(1L, 2L));
        service.releaseReindexSlot();
        assertTrue(service.bindReindexGuard(1L, 2L));
    }

    @Test
    @DisplayName("진행 중인 버전을 또 재색인하면 409로 쳐낸다 — 쳐낸 자리는 풀지 않는다")
    void 중복_재색인은_409() {
        EventVersionRepository versions = mock(EventVersionRepository.class);
        RagChunkRepository chunks = mock(RagChunkRepository.class);
        EmbeddingService service = service(versions, chunks);

        // 자리 주인(다른 스레드라고 가정)이 먼저 선점한 상태
        assertTrue(service.bindReindexGuard(1L, 10L));
        RagException e = assertThrows(RagException.class,
                () -> service.reindex(1L, 10L, null));
        assertEquals(HttpStatus.CONFLICT, e.getErrorCode().getHttpStatus());
        assertEquals("RAG409-0", e.getErrorCode().getCode());
        // 거절된 요청은 남의 자리를 해제하지 않는다 — 반납은 자리 주인 몫이다
        assertFalse(service.bindReindexGuard(1L, 10L));
        service.unbindReindexGuard(1L, 10L);
        assertTrue(service.bindReindexGuard(1L, 10L));
    }

    @Test
    @DisplayName("본문이 실패해도 자리는 반납된다 — finally")
    void 본문실패후_자리반납() {
        EventVersionRepository versions = mock(EventVersionRepository.class);
        RagChunkRepository chunks = mock(RagChunkRepository.class);
        EmbeddingService service = service(versions, chunks);
        when(versions.findByIdAndEventId(10L, 1L))
                .thenThrow(new EventException(EventErrorCode.VERSION_NOT_FOUND));

        assertThrows(EventException.class, () -> service.reindex(1L, 10L, null));
        // 실패로 끝난 실행의 finally 가 자리를 반납한다
        assertTrue(service.bindReindexGuard(1L, 10L));
    }

    @Test
    @DisplayName("재색인이 끝나면 자리를 반납한다 — finally")
    void 종료후_자리반납() {
        EventVersionRepository versions = mock(EventVersionRepository.class);
        RagChunkRepository chunks = mock(RagChunkRepository.class);
        EmbeddingService service = service(versions, chunks);
        when(versions.findByIdAndEventId(10L, 1L))
                .thenReturn(Optional.of(EventVersion.htmlOnly("   ")));

        assertEquals(0, service.reindex(1L, 10L, null));
        assertTrue(service.bindReindexGuard(1L, 10L));
    }
}
