package com.newvent.filtering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.newvent.auth.dto.AuthUser;
import com.newvent.editor.controller.EditController;
import com.newvent.editor.dto.EditRequest;
import com.newvent.editor.exception.EditException;
import com.newvent.editor.service.EditService;
import com.newvent.event.domain.Event;
import com.newvent.event.repository.EventRepository;
import com.newvent.generation.controller.GenerateController;
import com.newvent.generation.dto.GenerateRequest;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.GenerationException;
import com.newvent.generation.service.GenerationService;

class FilteringControllerTest {
    @Test
    void otherOwnerCannotStartEitherFlow() {
        Event event = mock(Event.class, RETURNS_DEEP_STUBS);
        EventRepository events = mock(EventRepository.class);
        when(events.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        when(event.getOwnerAdmin().getId()).thenReturn(2L);
        var edit = mock(EditService.class);
        var generate = mock(GenerationService.class);
        assertThrows(AccessDeniedException.class, () ->
                new EditController(edit, events).start(1L, new EditRequest("제목 수정"), AuthUser.admin(1L)));
        assertThrows(AccessDeniedException.class, () ->
                new GenerateController(generate, events).start(1L, new GenerateRequest(null, "생성"), AuthUser.admin(1L)));
        verifyNoInteractions(edit, generate);
    }

    @Test
    void ownerPassesClarificationIdToBothServices() {
        Event event = mock(Event.class, RETURNS_DEEP_STUBS);
        EventRepository events = mock(EventRepository.class);
        when(events.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        when(event.getOwnerAdmin().getId()).thenReturn(1L);
        when(event.getId()).thenReturn(1L);
        var edit = mock(EditService.class);
        var generate = mock(GenerationService.class);
        when(edit.start(any())).thenReturn(new EditService.StartResult.Rejected(GenerationErrorCode.EMPTY_REQUEST));
        when(generate.start(any())).thenReturn(new GenerationService.StartResult.Rejected(GenerationErrorCode.EMPTY_REQUEST));
        UUID previous = UUID.randomUUID();
        assertThrows(EditException.class, () ->
                new EditController(edit, events).start(1L, new EditRequest("제목 수정", previous), AuthUser.admin(1L)));
        assertThrows(GenerationException.class, () ->
                new GenerateController(generate, events).start(1L, new GenerateRequest(null, "생성", previous), AuthUser.admin(1L)));
        verify(edit).start(argThat(c -> previous.equals(c.clarificationJobId())));
        verify(generate).start(argThat(c -> previous.equals(c.clarificationJobId())));
    }
}
