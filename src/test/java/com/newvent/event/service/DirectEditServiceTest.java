package com.newvent.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.newvent.admin.domain.Admin;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.dto.request.ButtonStyle;
import com.newvent.event.dto.request.DirectEditRequest;
import com.newvent.event.dto.request.TextEdit;
import com.newvent.event.dto.response.DirectEditResponse;
import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.generation.service.ResourceTemplateLoader;
import com.newvent.generation.service.TemplateLoader;
import com.newvent.generation.service.VersionStore;
import com.newvent.user.domain.MembershipGrade;

@ExtendWith(MockitoExtension.class)
class DirectEditServiceTest {

    private static final TemplateLoader TEMPLATES = new ResourceTemplateLoader();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private VersionStore versionStore;

    private DirectEditService directEditService;

    @BeforeEach
    void setUp() {
        directEditService = new DirectEditService(eventRepository, versionStore, CLOCK);
    }

    @Test
    @DisplayName("직접 편집 성공 시 기준 HTML에 수정사항을 반영하고 새 버전으로 저장하여 반환한다")
    void directEdit_성공() {
        Long eventId = 1L;
        Long sourceVersionId = 12L;
        Event event = ownedEvent();
        String baseHtml = TEMPLATES.find("sports_cheer").orElseThrow().html();

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(versionStore.htmlOf(eventId, sourceVersionId)).thenReturn(Optional.of(baseHtml));
        when(versionStore.save(eq(eventId), any(String.class), eq(sourceVersionId)))
                .thenReturn(new VersionStore.Saved(13L, 5));

        DirectEditRequest request = new DirectEditRequest(
                sourceVersionId,
                List.of(new TextEdit(0, "LIVE PROMOTION", "REALTIME EVENT")),
                new ButtonStyle("#d60076", "#ffffff", "medium", "pill"));

        DirectEditResponse response = directEditService.directEdit(eventId, request, 1L);

        assertThat(response.versionId()).isEqualTo(13L);
        assertThat(response.versionNo()).isEqualTo(5);

        // 변경된 HTML이 저장소에 전달되었는지 검증
        verify(versionStore).save(eq(eventId), argThat(html ->
                html.contains("REALTIME EVENT") && html.contains("background-color: #d60076")
        ), eq(sourceVersionId));
    }

    @Test
    @DisplayName("이벤트가 존재하지 않거나 삭제되었으면 EVENT_NOT_FOUND 예외를 던진다")
    void directEdit_이벤트미존재() {
        Long eventId = 999L;
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        DirectEditRequest request = new DirectEditRequest(
                12L,
                List.of(new TextEdit(0, "LIVE PROMOTION", "새문구")),
                null);

        assertThatThrownBy(() -> directEditService.directEdit(eventId, request, 1L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND);

        verifyNoInteractions(versionStore);
    }

    @Test
    @DisplayName("기준 버전(sourceVersionId)이 존재하지 않으면 VERSION_NOT_FOUND 예외를 던진다")
    void directEdit_기준버전미존재() {
        Long eventId = 1L;
        Long sourceVersionId = 999L;
        Event event = ownedEvent();

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(versionStore.htmlOf(eventId, sourceVersionId)).thenReturn(Optional.empty());

        DirectEditRequest request = new DirectEditRequest(
                sourceVersionId,
                List.of(new TextEdit(0, "LIVE PROMOTION", "새문구")),
                null);

        assertThatThrownBy(() -> directEditService.directEdit(eventId, request, 1L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode())
                .isEqualTo(EventErrorCode.VERSION_NOT_FOUND);

        verify(versionStore, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("before 문구가 불일치하면 BEFORE_TEXT_MISMATCH(409) 예외가 발생한다")
    void directEdit_before불일치_409() {
        Long eventId = 1L;
        Long sourceVersionId = 12L;
        Event event = ownedEvent();
        String baseHtml = TEMPLATES.find("sports_cheer").orElseThrow().html();

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(versionStore.htmlOf(eventId, sourceVersionId)).thenReturn(Optional.of(baseHtml));

        DirectEditRequest request = new DirectEditRequest(
                sourceVersionId,
                List.of(new TextEdit(0, "전혀 다른 옛날 문구", "새문구")),
                null);

        assertThatThrownBy(() -> directEditService.directEdit(eventId, request, 1L))
                .isInstanceOf(DirectEditException.class)
                .extracting(ex -> ((DirectEditException) ex).getErrorCode())
                .isEqualTo(DirectEditErrorCode.BEFORE_TEXT_MISMATCH);

        verify(versionStore, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("수정할 내용이 아무것도 없으면 EMPTY_EDIT_REQUEST(400) 예외가 발생한다")
    void directEdit_수정내용없음_400() {
        assertThatThrownBy(() -> new DirectEditRequest(12L, null, null))
                .isInstanceOf(DirectEditException.class)
                .extracting(ex -> ((DirectEditException) ex).getErrorCode())
                .isEqualTo(DirectEditErrorCode.EMPTY_EDIT_REQUEST);
    }

    @Test
    @DisplayName("다른 관리자의 이벤트는 기준 HTML 조회나 새 버전 저장 전에 거부한다")
    void directEdit_다른_관리자_거부() {
        Long eventId = 1L;
        Event event = ownedEvent();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        DirectEditRequest request = new DirectEditRequest(12L,
                List.of(new TextEdit(0, "이전", "변경")), null);

        assertThatThrownBy(() -> directEditService.directEdit(eventId, request, 2L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(versionStore);
    }

    @Test
    @DisplayName("종료 상태이면 종료일과 관계없이 HTML 조회·저장 전에 409로 거절한다")
    void directEdit_종료상태_거부() {
        Event event = realOwnedEvent(NOW.plusDays(1));
        event.end();
        assertEndedRequestRejected(event);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    @DisplayName("게시 중 종료일이 지났거나 현재 시각과 같으면 HTML 조회·저장 전에 거절한다")
    void directEdit_게시종료일_거부(long secondsFromNow) {
        Event event = realOwnedEvent(NOW.plusSeconds(secondsFromNow));
        event.publish(mock(EventVersion.class));
        assertEndedRequestRejected(event);
    }

    private void assertEndedRequestRejected(Event event) {
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        DirectEditRequest request = new DirectEditRequest(12L,
                List.of(new TextEdit(0, "이전", "변경")), null);

        assertThatThrownBy(() -> directEditService.directEdit(1L, request, 1L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode())
                .isEqualTo(EventErrorCode.EVENT_ENDED_NOT_EDITABLE);
        verifyNoInteractions(versionStore);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("기간 지난 초안과 종료 전 게시 이벤트는 직접 편집할 수 있다")
    void directEdit_편집가능상태_유지(boolean published) {
        Event event = realOwnedEvent(published ? NOW.plusSeconds(1) : NOW.minusDays(1));
        if (published) event.publish(mock(EventVersion.class));
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        when(versionStore.htmlOf(1L, 12L)).thenReturn(Optional.of(
                "<section data-block='hero'><h1>이전</h1></section>"));
        when(versionStore.save(eq(1L), any(String.class), eq(12L)))
                .thenReturn(new VersionStore.Saved(13L, 2));

        var response = directEditService.directEdit(1L, new DirectEditRequest(12L,
                List.of(new TextEdit(0, "이전", "변경")), null), 1L);

        assertThat(response.versionId()).isEqualTo(13L);
        verify(versionStore).save(eq(1L), argThat(html -> html.contains("변경")), eq(12L));
    }

    private static Event realOwnedEvent(OffsetDateTime endAt) {
        Admin owner = mock(Admin.class);
        when(owner.getId()).thenReturn(1L);
        return Event.createDraft(owner, null, "이벤트", NOW.minusDays(2), endAt, MembershipGrade.NORMAL);
    }

    private static Event ownedEvent() {
        Event event = mock(Event.class);
        Admin owner = mock(Admin.class);
        when(event.getOwnerAdmin()).thenReturn(owner);
        when(owner.getId()).thenReturn(1L);
        return event;
    }


    // ── 블록 순서 ─────────────────────────────────────────────────
    //
    // ★ 새 엔드포인트를 만들지 않고 여기로 받는 이유 — 소유자 확인 · 종료 잠금 ·
    //   기준 버전 검증이 이미 위에 다 있다. 길을 하나 더 내면 두 벌로 유지해야 한다.
    //   위의 거부 테스트들이 순서 변경에도 그대로 적용된다는 뜻이기도 하다.

    private static final String 두_블록 =
            "<div class=\"ev-container event-page\">"
            + "<section data-block=\"benefits\"><ul><li>혜택</li></ul></section>"
            + "<section data-block=\"steps\"><ol><li>참여</li></ol></section></div>";

    @Test
    @DisplayName("블록 순서만 보내도 새 버전으로 저장된다 - 문구 수정이 없어도 된다")
    void directEdit_순서만_보내도_저장() {
        Event event = realOwnedEvent(NOW.plusDays(1));
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        when(versionStore.htmlOf(1L, 12L)).thenReturn(Optional.of(두_블록));
        when(versionStore.save(eq(1L), any(String.class), eq(12L)))
                .thenReturn(new VersionStore.Saved(13L, 2));

        var response = directEditService.directEdit(1L,
                new DirectEditRequest(12L, null, null, List.of("steps", "benefits")), 1L);

        assertThat(response.versionId()).isEqualTo(13L);
    }

    @Test
    @DisplayName("보낸 순서대로 세워서 저장한다 - 참여 방법이 혜택보다 앞으로")
    void directEdit_보낸_순서대로_저장() {
        Event event = realOwnedEvent(NOW.plusDays(1));
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        when(versionStore.htmlOf(1L, 12L)).thenReturn(Optional.of(두_블록));
        when(versionStore.save(eq(1L), any(String.class), eq(12L)))
                .thenReturn(new VersionStore.Saved(13L, 2));

        directEditService.directEdit(1L,
                new DirectEditRequest(12L, null, null, List.of("steps", "benefits")), 1L);

        verify(versionStore).save(eq(1L), argThat(html ->
                html.indexOf("data-block=\"steps\"") < html.indexOf("data-block=\"benefits\"")
                        && html.contains("data-block-order=\"steps,benefits\"")), eq(12L));
    }

    // ★ 조용히 무시하면 프론트는 오타를 낸 줄 모르고 관리자는 왜 안 움직이는지 모른다
    @Test
    @DisplayName("모르는 블록 이름이면 INVALID_BLOCK_ORDER(400) - 저장 전에 거절한다")
    void directEdit_모르는_블록이름_400() {
        assertThatThrownBy(() -> new DirectEditRequest(12L, null, null, List.of("steps", "없는블록")))
                .isInstanceOf(DirectEditException.class)
                .extracting(ex -> ((DirectEditException) ex).getErrorCode())
                .isEqualTo(DirectEditErrorCode.INVALID_BLOCK_ORDER);
        verifyNoInteractions(versionStore);
    }

    @Test
    @DisplayName("빈 순서 목록은 수정 내용으로 치지 않는다 - EMPTY_EDIT_REQUEST(400)")
    void directEdit_빈_순서목록_400() {
        assertThatThrownBy(() -> new DirectEditRequest(12L, null, null, List.of()))
                .isInstanceOf(DirectEditException.class)
                .extracting(ex -> ((DirectEditException) ex).getErrorCode())
                .isEqualTo(DirectEditErrorCode.EMPTY_EDIT_REQUEST);
    }
}
