package com.newvent.event.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

import com.newvent.admin.domain.Admin;
import com.newvent.admin.repository.AdminRepository;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventTemplate;
import com.newvent.event.dto.request.*;
import com.newvent.event.dto.response.EventDetailResponse;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.*;
import com.newvent.generation.service.*;

class TemplateLibraryServiceTest {
    EventTemplateRepository templates = mock(EventTemplateRepository.class);
    EventRepository events = mock(EventRepository.class);
    AdminRepository admins = mock(AdminRepository.class);
    VersionStore versions = mock(VersionStore.class);
    EventService eventService = mock(EventService.class);
    ResourceTemplateLoader resources = new ResourceTemplateLoader();
    TemplateService renderer = new TemplateService(new DbTemplateLoader(templates, resources));
    TemplateLibraryService service = new TemplateLibraryService(templates, events, admins, versions, renderer, eventService);
    Admin owner = mock(Admin.class);
    Event event = mock(Event.class);
    final String code = "custom_test";
    String html;

    @BeforeEach
    void setUp() {
        when(owner.getId()).thenReturn(1L);
        html = new TemplateService(resources).initialHtml("holiday_gift");
    }

    EventTemplate custom(boolean active) {
        var t = EventTemplate.custom(code, owner, "저장본", "설명", html);
        if (!active) t.deactivate();
        return t;
    }

    void source() {
        when(events.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(event));
        when(event.getOwnerAdmin()).thenReturn(owner);
        when(versions.htmlOf(2L, 7L)).thenReturn(Optional.of(html));
        when(admins.getReferenceById(1L)).thenReturn(owner);
        when(templates.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sports_cheer", "holiday_gift", "member_appreciation", "flash_sale", "pre_registration"})
    void registersEachBuiltinVersionAsIndependentCustom(String builtin) {
        html = new TemplateService(resources).initialHtml(builtin);
        source();
        var result = service.register(1L, new TemplateRegisterRequest(2L, 7L, " 새 템플릿 ", " 설명 "));
        assertFalse(result.builtin());
        assertTrue(result.templateKey().startsWith("custom_"));
        assertEquals("새 템플릿", result.name());
        verify(templates).save(argThat(t -> t.visibleTo(1L) && !t.visibleTo(9L)
                && t.getHtmlContent().contains("data-slot")));
        assertEquals(html, versions.htmlOf(2L, 7L).orElseThrow());
        verify(versions, never()).save(any(), any(), any());
    }

    @Test
    void cannotRegisterOtherOwnersVersion() {
        when(events.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(event));
        when(event.getOwnerAdmin()).thenReturn(owner);
        assertThrows(AccessDeniedException.class,
                () -> service.register(9L, new TemplateRegisterRequest(2L, 7L, "이름", null)));
        verifyNoInteractions(versions, templates);
    }

    @Test
    void versionMustBelongToSpecifiedEvent() {
        when(events.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(event));
        when(event.getOwnerAdmin()).thenReturn(owner);
        when(versions.htmlOf(2L, 7L)).thenReturn(Optional.empty());
        assertThrows(EventException.class,
                () -> service.register(1L, new TemplateRegisterRequest(2L, 7L, "이름", null)));
        verifyNoInteractions(templates);
    }

    @Test
    void invalidBlocksDoNotBecomeReusableTemplates() {
        source();
        when(versions.htmlOf(2L, 7L)).thenReturn(Optional.of(html.replace("data-block=\"hero\"", "data-block=\"unknown\"")));
        assertThrows(EventException.class,
                () -> service.register(1L, new TemplateRegisterRequest(2L, 7L, "이름", null)));
        verify(templates, never()).save(any());
    }

    @Test
    void generatedPageWithoutCtaLinkCanBeSavedAsTemplate() {
        source();
        String blank = com.newvent.registry.PageShell.plant(
                "<section data-block=\"hero\"><h1>새 이벤트</h1><p data-slot=\"period\"></p></section>"
                + "<section data-block=\"benefits\"><ul><li>혜택 하나</li><li>혜택 둘</li></ul></section>"
                + "<section data-block=\"cta\"><a href=\"#\">참여하기</a></section>", null);
        when(versions.htmlOf(2L, 7L)).thenReturn(Optional.of(blank));

        assertDoesNotThrow(() -> service.register(1L,
                new TemplateRegisterRequest(2L, 7L, "백지 생성 템플릿", null)));
        verify(templates).save(argThat(t -> t.getHtmlContent().contains("data-slot=\"period\"")
                && !t.getHtmlContent().contains("data-slot=\"cta-link\"")));
    }

    @Test
    void pageWithoutPeriodSlotCannotBeSavedAsTemplate() {
        source();
        when(versions.htmlOf(2L, 7L)).thenReturn(Optional.of(html.replace("data-slot=\"period\"", "")));
        assertThrows(EventException.class, () -> service.register(1L,
                new TemplateRegisterRequest(2L, 7L, "기간 누락", null)));
        verify(templates, never()).save(any());
    }

    @Test
    void privateTemplatesAreNotReadableOrSelectableByOthers() {
        when(templates.findByKey(code)).thenReturn(Optional.of(custom(true)));
        assertThrows(EventException.class, () -> service.preview(9L, code));
        assertThrows(EventException.class, () -> service.checkSelection(9L, code, false));
        assertThrows(EventException.class, () -> service.update(9L, code, new TemplateMetadataRequest("이름", null)));
        assertThrows(EventException.class, () -> service.deactivate(9L, code));
    }

    @Test
    void builtinsCannotBeEditedOrDisabled() {
        when(templates.findByKey("holiday_gift"))
                .thenReturn(Optional.of(EventTemplate.seed("holiday_gift", "기본", null, "", true)));
        assertThrows(EventException.class,
                () -> service.update(1L, "holiday_gift", new TemplateMetadataRequest("새 이름", null)));
        assertThrows(EventException.class, () -> service.deactivate(1L, "holiday_gift"));
    }

    @Test
    void inactiveTemplateCannotStartNewEventButExistingEventMayRegenerate() {
        when(templates.findByKey(code)).thenReturn(Optional.of(custom(false)));
        assertThrows(EventException.class, () -> service.checkSelection(1L, code, true));
        assertDoesNotThrow(() -> service.checkSelection(1L, code, false));
        assertThrows(EventException.class, () -> service.use(1L, code,
                new TemplateUseRequest("새 이벤트", OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), null)));
        verifyNoInteractions(eventService);
    }

    @Test
    void metadataChangeAndDisableLeaveSnapshotIntact() {
        var t = custom(true);
        when(templates.findByKey(code)).thenReturn(Optional.of(t));
        service.update(1L, code, new TemplateMetadataRequest("변경", null));
        service.deactivate(1L, code);
        assertEquals("변경", t.getName());
        assertFalse(t.isActive());
        assertEquals(html, t.getHtmlContent());
        verifyNoInteractions(events, versions);
    }

    @Test
    void useCreatesDraftAndItsFirstVersionWithoutChangingTemplate() {
        var t = custom(true);
        when(templates.findByKey(code)).thenReturn(Optional.of(t));
        var created = mock(EventDetailResponse.class);
        when(created.id()).thenReturn(20L);
        when(eventService.create(eq(1L), any())).thenReturn(created);
        when(versions.save(eq(20L), anyString(), isNull())).thenReturn(new VersionStore.Saved(21L, 1));
        var request = new TemplateUseRequest("새 이벤트", OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), null);
        var result = service.use(1L, code, request);
        assertEquals(20L, result.eventId());
        assertEquals(21L, result.versionId());
        assertEquals(1, result.versionNo());
        verify(eventService).create(eq(1L), argThat(r -> r.templateKey().equals(code)));
        assertEquals(html, t.getHtmlContent());
    }

    @Test
    void librarySearchPassesOwnerAndEscapesWildcardCharacters() {
        when(templates.findLibrary(eq(1L), eq("%50!%!_!!%"), isNull(), eq(false), any()))
                .thenReturn(new PageImpl<>(java.util.List.of()));
        assertEquals(0, service.list(1L, "50%_!", null, false, 0, 12).totalElements());
    }
}
