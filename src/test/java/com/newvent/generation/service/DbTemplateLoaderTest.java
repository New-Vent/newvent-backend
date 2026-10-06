package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.newvent.admin.domain.Admin;
import com.newvent.event.domain.EventTemplate;
import com.newvent.event.repository.EventTemplateRepository;

class DbTemplateLoaderTest {
    final EventTemplateRepository repository = mock(EventTemplateRepository.class);
    final ResourceTemplateLoader resources = new ResourceTemplateLoader();
    final DbTemplateLoader loader = new DbTemplateLoader(repository, resources);

    @Test
    void builtinUsesRealResourceInsteadOfDatabasePlaceholder() {
        when(repository.findByKey("sports_cheer")).thenReturn(Optional.of(
                EventTemplate.seed("sports_cheer", "응원", null, "<!-- placeholder -->", true)));
        assertEquals(resources.find("sports_cheer").orElseThrow().html(),
                loader.find("sports_cheer").orElseThrow().html());
    }

    @Test
    void customSnapshotIsAvailableToGenerationEvenAfterDisabling() {
        var t = EventTemplate.custom("custom_test", mock(Admin.class), "등록", null, "<section>복사본</section>");
        t.deactivate();
        when(repository.findByKey("custom_test")).thenReturn(Optional.of(t));
        assertEquals(t.getHtmlContent(), loader.find("custom_test").orElseThrow().html());
        assertFalse(loader.find("custom_test").orElseThrow().builtin());
        assertTrue(loader.all().stream().allMatch(TemplateLoader.Source::builtin));
    }

    @Test
    void missingDatabaseTemplateIsNotResurrectedFromResources() {
        when(repository.findByKey("sports_cheer")).thenReturn(Optional.empty());
        assertTrue(loader.find("sports_cheer").isEmpty());
    }
}
