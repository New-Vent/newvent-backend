package com.newvent.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.newvent.admin.domain.Admin;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.dto.response.DirectEditResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.generation.service.VersionStore;

/**
 * 저장 지점으로 되돌리기.
 *
 * ★ 이 테스트가 지키는 계약 셋
 *     ① 되돌리기는 <b>새 버전</b>이다 — 기존 버전을 지우거나 version_no 를 되감지 않는다
 *     ② source_version_id 가 되돌린 원본을 가리킨다 — "언제 무엇으로 되돌렸나" 가 남는다
 *     ③ 남의 이벤트 버전은 403 이 아니라 <b>"없음"</b> 이다
 *
 * ★ 지우지 않는다는 것을 어떻게 보나
 *   versionStore 는 목이라 "지웠다" 를 직접 볼 수 없다. 대신 <b>save 외의 쓰기가
 *   한 번도 안 일어났다</b> 는 것을 verifyNoMoreInteractions 로 본다.
 *   실제 DB 동작은 JpaVersionStoreTest 가 본다.
 */
@ExtendWith(MockitoExtension.class)
class VersionRestoreServiceTest {

    private static final Long EVENT_ID = 7L;
    private static final Long ADMIN_ID = 3L;
    private static final Long V2_ID = 22L;

    private static final String V2_HTML =
            "<div class=\"ev-container event-page theme-sports\">"
            + "<section data-block=\"hero\"><h1>되돌릴 내용</h1></section></div>";

    @Mock private EventRepository eventRepository;
    @Mock private EventVersionRepository eventVersionRepository;
    @Mock private VersionStore versionStore;

    private VersionRestoreService service;

    @BeforeEach
    void setUp() {
        service = new VersionRestoreService(eventRepository, eventVersionRepository, versionStore);
    }

    // ── 준비물 ───────────────────────────────────────────────────

    private Event ownedEvent() {
        Admin owner = mock(Admin.class);
        when(owner.getId()).thenReturn(ADMIN_ID);
        Event event = mock(Event.class);
        when(event.getOwnerAdmin()).thenReturn(owner);
        return event;
    }

    private void 이벤트가_있다(Event event) {
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
    }

    /**
     * ★ 저장 지점(checkpoint) 여부를 보지 않는다.
     *   버전 목록(getVersions)이 그 이벤트의 버전을 전부 주므로, 되돌리기만 저장 지점으로
     *   조이면 <b>목록에 보이는데 누르면 404 가 나는 버전</b>이 생긴다.
     */
    private void v2_가_있다() {
        EventVersion v2 = mock(EventVersion.class);
        when(v2.getHtmlContent()).thenReturn(V2_HTML);
        when(eventVersionRepository.findByIdAndEventId(V2_ID, EVENT_ID))
                .thenReturn(Optional.of(v2));
    }

    // ── 되돌리기 ─────────────────────────────────────────────────

    @Test
    @DisplayName("★ 그 버전의 HTML 로 새 버전을 만들고, source 는 되돌린 원본을 가리킨다")
    void 되돌리면_새_버전이_생긴다() {
        이벤트가_있다(ownedEvent());
        v2_가_있다();
        when(versionStore.save(EVENT_ID, V2_HTML, V2_ID))
                .thenReturn(new VersionStore.Saved(99L, 9));

        DirectEditResponse res = service.restore(EVENT_ID, V2_ID, ADMIN_ID);

        // ★ 내용은 v2 그대로, source 는 v2, 번호는 새로 매겨진 9
        verify(versionStore).save(EVENT_ID, V2_HTML, V2_ID);
        assertThat(res.versionId()).isEqualTo(99L);
        assertThat(res.versionNo()).isEqualTo(9);
    }

    @Test
    @DisplayName("★ 기존 버전을 지우거나 되감지 않는다 — 쓰기는 save 한 번뿐")
    void 이력을_건드리지_않는다() {
        이벤트가_있다(ownedEvent());
        v2_가_있다();
        when(versionStore.save(EVENT_ID, V2_HTML, V2_ID))
                .thenReturn(new VersionStore.Saved(99L, 9));

        service.restore(EVENT_ID, V2_ID, ADMIN_ID);

        // ★ delete 나 번호 되감기가 끼어들면 여기서 걸린다.
        //   되감으면 uk_event_versions_event_version_no 와 싸우고,
        //   "언제 무엇으로 되돌렸나" 가 이력에서 사라진다.
        verify(versionStore).save(EVENT_ID, V2_HTML, V2_ID);
        verifyNoMoreInteractions(versionStore);
        verify(eventVersionRepository, never()).delete(any());
        verify(eventVersionRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("되돌린 버전을 또 되돌릴 수 있다 — 연속 되돌리기")
    void 연속으로_되돌린다() {
        Long v9Id = 99L;
        이벤트가_있다(ownedEvent());
        v2_가_있다();

        EventVersion v9 = mock(EventVersion.class);
        when(v9.getHtmlContent()).thenReturn(V2_HTML);
        when(eventVersionRepository.findByIdAndEventId(v9Id, EVENT_ID))
                .thenReturn(Optional.of(v9));

        when(versionStore.save(eq(EVENT_ID), eq(V2_HTML), eq(V2_ID)))
                .thenReturn(new VersionStore.Saved(v9Id, 9));
        when(versionStore.save(eq(EVENT_ID), eq(V2_HTML), eq(v9Id)))
                .thenReturn(new VersionStore.Saved(100L, 10));

        service.restore(EVENT_ID, V2_ID, ADMIN_ID);
        DirectEditResponse second = service.restore(EVENT_ID, v9Id, ADMIN_ID);

        assertThat(second.versionNo()).isEqualTo(10);
        verify(versionStore).save(EVENT_ID, V2_HTML, v9Id);
    }

    // ── 거부 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("그 이벤트의 버전이 아니면 VERSION_NOT_FOUND")
    void 없는_버전은_거부한다() {
        이벤트가_있다(ownedEvent());
        when(eventVersionRepository.findByIdAndEventId(V2_ID, EVENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(EVENT_ID, V2_ID, ADMIN_ID))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode())
                .isEqualTo(EventErrorCode.VERSION_NOT_FOUND);

        verify(versionStore, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("★ 남의 이벤트 버전도 '없음' 이다 — 403 이면 버전 id 를 훑어 남의 이벤트를 셀 수 있다")
    void 남의_버전은_없음이다() {
        이벤트가_있다(ownedEvent());
        // ★ 쿼리에 eventId 가 걸려 있어 남의 버전은 애초에 안 나온다
        when(eventVersionRepository.findByIdAndEventId(999L, EVENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(EVENT_ID, 999L, ADMIN_ID))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode())
                .isEqualTo(EventErrorCode.VERSION_NOT_FOUND);
    }

    @Test
    @DisplayName("소유자가 아니면 거부한다 — 버전을 읽기도 전에")
    void 소유자가_아니면_거부한다() {
        이벤트가_있다(ownedEvent());

        assertThatThrownBy(() -> service.restore(EVENT_ID, V2_ID, 999L))
                .isInstanceOf(AccessDeniedException.class);

        // ★ 소유자 검사가 버전 조회보다 먼저여야 한다.
        //   뒤에 두면 "그 이벤트에 그 버전이 있나" 를 남이 알아낼 수 있다.
        verify(eventVersionRepository, never())
                .findByIdAndEventId(any(), any());
        verify(versionStore, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("지워진 이벤트는 EVENT_NOT_FOUND")
    void 지워진_이벤트는_거부한다() {
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(EVENT_ID, V2_ID, ADMIN_ID))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND);

        verify(versionStore, never()).save(any(), any(), any());
    }
}
