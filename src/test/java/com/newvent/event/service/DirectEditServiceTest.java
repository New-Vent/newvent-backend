package com.newvent.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.newvent.event.domain.Event;
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

@ExtendWith(MockitoExtension.class)
class DirectEditServiceTest {

    private static final TemplateLoader TEMPLATES = new ResourceTemplateLoader();

    @Mock
    private EventRepository eventRepository;

    @Mock
    private VersionStore versionStore;

    private DirectEditService directEditService;

    @BeforeEach
    void setUp() {
        directEditService = new DirectEditService(eventRepository, versionStore);
    }

    @Test
    @DisplayName("직접 편집 성공 시 기준 HTML에 수정사항을 반영하고 새 버전으로 저장하여 반환한다")
    void directEdit_성공() {
        Long eventId = 1L;
        Long sourceVersionId = 12L;
        Event event = mock(Event.class);
        String baseHtml = TEMPLATES.find("template_1_sports_cheer").orElseThrow().html();

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(versionStore.htmlOf(eventId, sourceVersionId)).thenReturn(Optional.of(baseHtml));
        when(versionStore.save(eq(eventId), any(String.class), eq(sourceVersionId)))
                .thenReturn(new VersionStore.Saved(13L, 5));

        DirectEditRequest request = new DirectEditRequest(
                sourceVersionId,
                List.of(new TextEdit(0, "LIVE PROMOTION", "REALTIME EVENT")),
                new ButtonStyle("#d60076", "#ffffff", "medium", "pill"));

        DirectEditResponse response = directEditService.directEdit(eventId, request);

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

        assertThatThrownBy(() -> directEditService.directEdit(eventId, request))
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
        Event event = mock(Event.class);

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(versionStore.htmlOf(eventId, sourceVersionId)).thenReturn(Optional.empty());

        DirectEditRequest request = new DirectEditRequest(
                sourceVersionId,
                List.of(new TextEdit(0, "LIVE PROMOTION", "새문구")),
                null);

        assertThatThrownBy(() -> directEditService.directEdit(eventId, request))
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
        Event event = mock(Event.class);
        String baseHtml = TEMPLATES.find("template_1_sports_cheer").orElseThrow().html();

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(versionStore.htmlOf(eventId, sourceVersionId)).thenReturn(Optional.of(baseHtml));

        DirectEditRequest request = new DirectEditRequest(
                sourceVersionId,
                List.of(new TextEdit(0, "전혀 다른 옛날 문구", "새문구")),
                null);

        assertThatThrownBy(() -> directEditService.directEdit(eventId, request))
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
}
