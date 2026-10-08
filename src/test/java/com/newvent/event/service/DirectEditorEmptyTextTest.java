package com.newvent.event.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import com.newvent.event.dto.request.TextEdit;
import com.newvent.generation.service.ResourceTemplateLoader;
import com.newvent.registry.BlockValidator;

class DirectEditorEmptyTextTest {
    @Test
    void emptyTextRetainsEveryIndexAndCanBeRefilled() {
        for (var template : new ResourceTemplateLoader().all()) {
            var original = DirectEditor.editableTexts(template.html());
            String empty = DirectEditor.applyTextEdits(template.html(),
                    List.of(new TextEdit(0, original.get(0).before(), "")));
            var collected = DirectEditor.editableTexts(empty);
            assertEquals(original.size(), collected.size(), template.code());
            assertEquals("", collected.get(0).before());
            assertEquals(original.subList(1, original.size()), collected.subList(1, collected.size()));
            String other = DirectEditor.applyTextEdits(empty,
                    List.of(new TextEdit(1, original.get(1).before(), "Other text")));
            String refilled = DirectEditor.applyTextEdits(other,
                    List.of(new TextEdit(0, "", "Restored")));
            assertEquals("Restored", DirectEditor.editableTexts(refilled).get(0).before());
            assertEquals("Other text", DirectEditor.editableTexts(refilled).get(1).before());
            assertEquals(original.size(), DirectEditor.editableTexts(refilled).size());
        }
    }

    @Test
    void multilineSurvivesSerializationAndFurtherEdits() {
        String base = "<section data-block=\"hero\"><h1>Title</h1></section>";
        String edited = DirectEditor.applyTextEdits(base,
                List.of(new TextEdit(0, "Title", "First\nSecond")));
        assertEquals("First\nSecond", DirectEditor.editableTexts(edited).get(0).before());
        assertTrue(Jsoup.parseBodyFragment(edited).selectFirst("span").attr("style").contains("pre-line"));
        String again = DirectEditor.applyTextEdits(edited,
                List.of(new TextEdit(0, "First\nSecond", "Third\nFourth")));
        assertEquals("Third\nFourth", DirectEditor.editableTexts(again).get(0).before());
        assertEquals(1, Jsoup.parseBodyFragment(again).select("span").size());
    }

    @Test
    void editedSanitizerKeepsEmptyHolderAndLineBreakStyle() {
        String base = "<section data-block=\"hero\"><p>Title</p></section>";
        for (String value : List.of("", "First\nSecond", "<script>bad()</script>")) {
            String edited = DirectEditor.applyTextEdits(base, List.of(new TextEdit(0, "Title", value)));
            String clean = BlockValidator.sanitizeEdited(edited);
            assertEquals(value, DirectEditor.editableTexts(clean).get(0).before());
            assertNotNull(Jsoup.parseBodyFragment(clean).getElementById("nv-direct-text-0"));
            assertTrue(clean.contains("white-space"));
            assertFalse(clean.contains("<script>bad()"));
        }
    }
}
