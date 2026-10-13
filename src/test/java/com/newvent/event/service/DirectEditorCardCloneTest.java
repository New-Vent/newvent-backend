package com.newvent.event.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import com.newvent.event.dto.request.TextEdit;
import com.newvent.registry.Block;
import com.newvent.registry.BlockValidator;

class DirectEditorCardCloneTest {
    @Test
    void repeatedCopiesHaveUniqueIdsAndRemainEditable() {
        String base = "<section data-block=\"benefits\"><div class=\"benefits-list\">"
                + "<div class=\"benefit-card\">First</div><div class=\"benefit-card\">Second</div>"
                + "</div></section>";
        String edited = DirectEditor.applyTextEdits(base, List.of(new TextEdit(1, "Second", "Edited")));
        String cloned = BlockValidator.duplicateCard(edited, Block.BENEFITS);
        cloned = BlockValidator.duplicateCard(cloned, Block.BENEFITS);
        var holders = Jsoup.parseBodyFragment(cloned).select("span[id^=nv-direct-text-]");
        assertEquals(3, holders.size());
        assertEquals(3, holders.eachAttr("id").stream().distinct().count());
        assertEquals(4, DirectEditor.editableTexts(cloned).size());
        assertEquals("Edited", DirectEditor.editableTexts(cloned).get(1).before());
        String refilled = DirectEditor.applyTextEdits(cloned, List.of(new TextEdit(3, "", "New card")));
        assertEquals("Edited", DirectEditor.editableTexts(refilled).get(1).before());
        assertEquals("New card", DirectEditor.editableTexts(refilled).get(3).before());
    }
}
