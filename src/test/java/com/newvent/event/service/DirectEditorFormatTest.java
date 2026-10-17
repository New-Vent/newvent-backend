package com.newvent.event.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.newvent.event.dto.request.TextEdit;
import com.newvent.event.dto.request.TextFormat;
import com.newvent.event.exception.DirectEditException;
import com.newvent.generation.service.ResourceTemplateLoader;

class DirectEditorFormatTest {
    private static final String HTML = "<section data-block='benefits'><div class='card'><p>첫째</p></div>"
            + "<div class='card'><p>둘째</p></div></section>";

    @Test
    void formatAndMultilinePersistAcrossEveryTemplate() {
        for (var template : new ResourceTemplateLoader().all()) {
            var original = DirectEditor.editableTexts(template.html());
            String edited = DirectEditor.applyTextEdits(template.html(), List.of(
                    new TextEdit(0, original.get(0).before(), "첫줄\n둘째줄", new TextFormat("#d60076", 24, true, false))));
            var texts = DirectEditor.editableTexts(edited);
            assertEquals(original.size(), texts.size());
            assertEquals("첫줄\n둘째줄", texts.get(0).before());
            assertEquals("24px", texts.get(0).format().get("font-size"));
            assertEquals("700", texts.get(0).format().get("font-weight"));
            String reset = DirectEditor.applyTextEdits(edited, List.of(
                    new TextEdit(0, texts.get(0).before(), "", new TextFormat(null, null, null, true))));
            assertTrue(DirectEditor.editableTexts(reset).get(0).format().isEmpty());
            assertEquals(original.size(), DirectEditor.editableTexts(reset).size());
            assertTrue(reset.contains("white-space: pre-line"));
        }
    }

    @Test
    void invalidFormatDuplicateAndStaleRequestsFail() {
        assertThrows(DirectEditException.class, () -> new TextFormat("red;display:none", null, null, false));
        assertThrows(DirectEditException.class, () -> new TextFormat(null, 9, null, false));
        assertThrows(DirectEditException.class, () -> new TextFormat(null, 97, null, false));
        String html = "<section data-block='hero'><h1>Title</h1></section>";
        assertThrows(DirectEditException.class, () -> DirectEditor.applyTextEdits(html,
                List.of(new TextEdit(0, "Title", "A"), new TextEdit(0, "Title", "B"))));
        assertThrows(DirectEditException.class, () -> DirectEditor.applyTextEdits(html,
                List.of(new TextEdit(0, "Old", "New", new TextFormat(null, 24, null, false)))));
    }
}
