package com.newvent.event.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import com.newvent.event.dto.request.TextEdit;
import com.newvent.generation.service.SavedTemplateHtml;
import com.newvent.registry.PageShell;
import com.newvent.registry.Slots;

class DirectEditorNoticesTest {
    @Test
    void noticesSurviveRenderingAndTemplateReuse() {
        String html = "<section data-block=\"hero\"><h1>Title</h1>"
                + "<p data-slot=\"period\"></p></section>"
                + "<section data-block=\"notices\"><ul><li>Original notice</li></ul></section>";
        var target = DirectEditor.editableTexts(html).get(1);
        String edited = DirectEditor.applyTextEdits(html,
                List.of(new TextEdit(target.index(), target.before(), "Custom notice\nSecond line")));
        String rendered = Slots.fill(PageShell.ensureNotices(edited), "Period", "/join");
        assertTrue(rendered.contains("Custom notice"));
        assertFalse(rendered.contains("Original notice"));
        String template = SavedTemplateHtml.clean(edited);
        assertTrue(template.contains("Custom notice"));
        assertEquals(1, Jsoup.parseBodyFragment(template).select("[data-block=notices]").size());
    }

    @Test
    void emptyNoticeCanBeRefilledAndHtmlIsEscaped() {
        String html = "<section data-block=\"notices\"><p>Original</p><p>Other</p></section>";
        String empty = DirectEditor.applyTextEdits(html, List.of(new TextEdit(0, "Original", "")));
        assertEquals(2, DirectEditor.editableTexts(empty).size());
        String refilled = DirectEditor.applyTextEdits(empty,
                List.of(new TextEdit(0, "", "<script>alert(1)</script>")));
        assertTrue(refilled.contains("&lt;script&gt;"));
        assertTrue(Jsoup.parseBodyFragment(refilled).select("script").isEmpty());
        assertEquals("Other", DirectEditor.editableTexts(refilled).get(1).before());
    }
}
