package com.newvent.event.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jsoup.Jsoup;
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
                    new TextEdit(0, original.get(0).before(), "첫줄\n둘째줄", new TextFormat("#d60076", 24, true, false), false)));
            var texts = DirectEditor.editableTexts(edited);
            assertEquals(original.size(), texts.size());
            assertEquals("첫줄\n둘째줄", texts.get(0).before());
            assertEquals("24px", texts.get(0).format().get("font-size"));
            assertEquals("700", texts.get(0).format().get("font-weight"));
            String reset = DirectEditor.applyTextEdits(edited, List.of(
                    new TextEdit(0, texts.get(0).before(), "", new TextFormat(null, null, null, true), false)));
            assertTrue(DirectEditor.editableTexts(reset).get(0).format().isEmpty());
            assertEquals(original.size(), DirectEditor.editableTexts(reset).size());
            assertTrue(reset.contains("white-space: pre-line"));
        }
    }

    @Test
    void deletionRemovesEmptyContainerAndOtherEditUsesOriginalIndex() {
        assertEquals(1, DirectEditor.editableTexts(HTML).get(0).deleteDepth());
        String result = DirectEditor.applyTextEdits(HTML, List.of(
                new TextEdit(0, "첫째", "첫째", null, true),
                new TextEdit(1, "둘째", "새 문구", new TextFormat(null, 30, false, false), false)));
        assertEquals(1, Jsoup.parseBodyFragment(result).select(".card").size());
        var remaining = DirectEditor.editableTexts(result);
        assertEquals(1, remaining.size());
        assertEquals("새 문구", remaining.get(0).before());
        String again = DirectEditor.applyTextEdits(result, List.of(new TextEdit(0, "새 문구", "다음 수정")));
        assertEquals("다음 수정", DirectEditor.editableTexts(again).get(0).before());
    }

    @Test
    void emptyTextKeepsPositionWhileExplicitDeleteRemovesIt() {
        String empty = DirectEditor.applyTextEdits(HTML, List.of(new TextEdit(0, "첫째", "")));
        assertEquals(2, DirectEditor.editableTexts(empty).size());
        String removed = DirectEditor.applyTextEdits(empty, List.of(new TextEdit(0, "", "", null, true)));
        assertEquals(1, DirectEditor.editableTexts(removed).size());
        assertEquals(1, Jsoup.parseBodyFragment(removed).select(".card").size());
    }

    @Test
    void protectedElementsCannotBeDeletedEvenByCraftedRequest() {
        for (String html : List.of(
                "<section data-block='notices'><p>약관</p></section>",
                "<section data-block='cta'><a data-slot='cta-link'>참여</a></section>",
                "<section data-block='benefits'><p id='counter'><span>수량</span></p></section>",
                "<section data-block='hero'>제목</section>")) {
            var first = DirectEditor.editableTexts(html).get(0);
            assertNull(first.deleteDepth());
            assertThrows(DirectEditException.class, () -> DirectEditor.applyTextEdits(html,
                    List.of(new TextEdit(0, first.before(), first.before(), null, true))));
        }
    }

    @Test
    void requiredHeadingAndLastBenefitStayProtected() {
        String title = "<section data-block='hero'><h1><span>제목</span></h1></section>";
        assertNull(DirectEditor.editableTexts(title).get(0).deleteDepth());
        String cards = "<section data-block='benefits'><div class='benefit-card'>첫째</div><div class='benefit-card'>둘째</div></section>";
        assertNotNull(DirectEditor.editableTexts(cards).get(0).deleteDepth());
        assertThrows(DirectEditException.class, () -> DirectEditor.applyTextEdits(cards, List.of(
                new TextEdit(0, "첫째", "첫째", null, true), new TextEdit(1, "둘째", "둘째", null, true))));
        String remaining = DirectEditor.applyTextEdits(cards, List.of(new TextEdit(0, "첫째", "첫째", null, true)));
        assertNull(DirectEditor.editableTexts(remaining).get(0).deleteDepth());
    }

    @Test
    void deletionDoesNotRemoveSiblingTextOrImage() {
        String base = "<section data-block='benefits'><div><p>첫째</p><p>둘째</p><img src='/a.png'></div></section>";
        String result = DirectEditor.applyTextEdits(base, List.of(new TextEdit(0, "첫째", "첫째", null, true)));
        assertEquals("둘째", Jsoup.parseBodyFragment(result).selectFirst("div p").text());
        assertEquals(1, Jsoup.parseBodyFragment(result).select("img").size());
    }

    @Test
    void invalidFormatDuplicateAndStaleRequestsFail() {
        assertThrows(DirectEditException.class, () -> new TextFormat("red;display:none", null, null, false));
        assertThrows(DirectEditException.class, () -> new TextFormat(null, 9, null, false));
        assertThrows(DirectEditException.class, () -> new TextFormat(null, 97, null, false));
        assertThrows(DirectEditException.class, () -> DirectEditor.applyTextEdits(HTML,
                List.of(new TextEdit(0, "첫째", "변경"), new TextEdit(0, "첫째", "", null, true))));
        assertThrows(DirectEditException.class, () -> DirectEditor.applyTextEdits(HTML,
                List.of(new TextEdit(0, "오래된 문구", "", null, true))));
    }
}
