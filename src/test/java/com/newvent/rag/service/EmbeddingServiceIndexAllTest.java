package com.newvent.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.newvent.event.domain.Event;
import com.newvent.rag.dto.response.ReindexAllResponse;

/** indexAllEvents 단위 테스트. DB 없이 돈다 — reindex()만 가짜로 갈아끼운다. */
class EmbeddingServiceIndexAllTest {

    static final class FakeIndexAll extends EmbeddingService {
        private final java.util.Map<Long, Integer> results = new java.util.HashMap<>();
        private final List<Long> exploding = new java.util.ArrayList<>();

        FakeIndexAll(com.newvent.event.repository.EventRepository events) {
            super(null, null, null, null, events, null, noTx());
        }

        // DB 없이 돈다 — 트랜잭션 경계만 흉내 내고 실제 커밋은 없다
        private static TransactionTemplate noTx() {
            PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
            when(tm.getTransaction(any())).thenAnswer(inv -> mock(TransactionStatus.class));
            return new TransactionTemplate(tm);
        }

        void willReturn(Long eventId, int chunks) { results.put(eventId, chunks); }
        void willExplode(Long eventId) { exploding.add(eventId); }

        @Override
        public int reindex(Long eventId, Long versionId) {
            if (exploding.contains(eventId)) throw new RuntimeException("색인 터짐");
            return results.getOrDefault(eventId, 0);
        }
    }

    private static Event event(long id, String title) {
        Event e = mock(Event.class);
        when(e.getId()).thenReturn(id);
        when(e.getTitle()).thenReturn(title);
        return e;
    }

    private EmbeddingService indexing(com.newvent.event.repository.EventRepository events) {
        return new FakeIndexAll(events);
    }

    @Test
    @DisplayName("전체 성공하면 합계가 맞는다")
    void allSucceed() {
        com.newvent.event.repository.EventRepository events = mock(com.newvent.event.repository.EventRepository.class);
        List<Event> targets = List.of(event(1L, "여름"), event(2L, "가을"));
        when(events.findAllByDeletedAtIsNull()).thenReturn(targets);
        FakeIndexAll service = (FakeIndexAll) indexing(events);
        service.willReturn(1L, 10);
        service.willReturn(2L, 5);

        ReindexAllResponse out = service.indexAllEvents();

        assertEquals(2, out.totalEvents());
        assertEquals(2, out.succeeded());
        assertTrue(out.failedEvents().isEmpty());
        assertEquals(15, out.totalChunks());
    }

    @Test
    @DisplayName("하나가 터져도 멈추지 않고 다음으로 넘어간다")
    void partialFailureContinues() {
        com.newvent.event.repository.EventRepository events = mock(com.newvent.event.repository.EventRepository.class);
        List<Event> targets = List.of(event(1L, "여름"), event(2L, "고장"), event(3L, "가을"));
        when(events.findAllByDeletedAtIsNull()).thenReturn(targets);
        FakeIndexAll service = (FakeIndexAll) indexing(events);
        service.willReturn(1L, 10);
        service.willExplode(2L);
        service.willReturn(3L, 5);

        ReindexAllResponse out = service.indexAllEvents();

        assertEquals(3, out.totalEvents());
        assertEquals(2, out.succeeded());
        assertEquals(List.of(new ReindexAllResponse.FailedEvent(2L, "색인 터짐")), out.failedEvents());
        assertEquals(15, out.totalChunks());
    }

    @Test
    @DisplayName("SEED: 데이터는 건너뛴다")
    void seedSkipped() {
        com.newvent.event.repository.EventRepository events = mock(com.newvent.event.repository.EventRepository.class);
        List<Event> targets = List.of(event(1L, "SEED: 데모"), event(2L, "실제"));
        when(events.findAllByDeletedAtIsNull()).thenReturn(targets);
        FakeIndexAll service = (FakeIndexAll) indexing(events);
        service.willReturn(2L, 7);

        ReindexAllResponse out = service.indexAllEvents();

        assertEquals(1, out.totalEvents());
        assertEquals(1, out.succeeded());
        assertEquals(7, out.totalChunks());
    }

    @Test
    @DisplayName("앞뒤 공백·대소문자가 달라도 SEED: 로 보면 건너뛴다")
    void seedVariantsSkipped() {
        com.newvent.event.repository.EventRepository events = mock(com.newvent.event.repository.EventRepository.class);
        List<Event> targets = List.of(
                event(1L, " seed: 공백"), event(2L, "Seed: 대문자"), event(3L, "실제"));
        when(events.findAllByDeletedAtIsNull()).thenReturn(targets);
        FakeIndexAll service = (FakeIndexAll) indexing(events);
        service.willReturn(3L, 4);

        ReindexAllResponse out = service.indexAllEvents();

        assertEquals(1, out.totalEvents());
        assertEquals(1, out.succeeded());
        assertEquals(4, out.totalChunks());
    }

    @Test
    @DisplayName("자리 선점은 한 번만 잡힌다 — REST·스케줄러가 같은 자리를 쓴다")
    void claimOnce() {
        com.newvent.event.repository.EventRepository events = mock(com.newvent.event.repository.EventRepository.class);
        EmbeddingService service = indexing(events);

        assertTrue(service.tryClaimReindexSlot());
        assertFalse(service.tryClaimReindexSlot());
    }
}
