package com.newvent.generation.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.newvent.auth.dto.AuthUser;
import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.service.TemplateLibraryService;
import com.newvent.generation.dto.GenerateRequest;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.GenerationException;
import com.newvent.generation.service.GenerationService;

class GenerationTemplateAccessTest {
    final EventRepository events = mock(EventRepository.class);
    final GenerationService generation = mock(GenerationService.class);
    final TemplateLibraryService library = mock(TemplateLibraryService.class);
    final GenerateController controller = new GenerateController(generation, events, library);

    Event ownEvent(String templateCode) {
        Event event = mock(Event.class, RETURNS_DEEP_STUBS);
        when(event.getOwnerAdmin().getId()).thenReturn(1L);
        when(event.getId()).thenReturn(2L);
        when(event.templateCode()).thenReturn(templateCode);
        when(events.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(event));
        return event;
    }

    @Test
    void explicitSelectionMustBeOwnedAndActiveBeforeJobStarts() {
        ownEvent(null);
        doThrow(new EventException(EventErrorCode.TEMPLATE_NOT_FOUND))
                .when(library).checkSelection(1L, "custom_other", true);
        assertThrows(EventException.class, () ->
                controller.start(2L, new GenerateRequest("custom_other", null), AuthUser.admin(1L)));
        verifyNoInteractions(generation);
    }

    @Test
    void linkedTemplateRegenerationDoesNotRequireActiveState() {
        ownEvent("custom_test");
        when(generation.start(any())).thenReturn(new GenerationService.StartResult.Rejected(GenerationErrorCode.EMPTY_REQUEST));
        assertThrows(GenerationException.class, () ->
                controller.start(2L, new GenerateRequest(null, null), AuthUser.admin(1L)));
        verify(library).checkSelection(1L, "custom_test", false);
    }

    @Test
    void explicitBlankGenerationDoesNotCheckTemplateLibrary() {
        ownEvent("custom_test");
        when(generation.start(any())).thenReturn(new GenerationService.StartResult.Rejected(GenerationErrorCode.EMPTY_REQUEST));
        assertThrows(GenerationException.class, () ->
                controller.start(2L, new GenerateRequest("", "새 이벤트"), AuthUser.admin(1L)));
        verifyNoInteractions(library);
    }
}
