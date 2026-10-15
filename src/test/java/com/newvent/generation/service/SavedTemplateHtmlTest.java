package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.newvent.registry.Block;
import com.newvent.registry.Slot;
import com.newvent.registry.Slots;

class SavedTemplateHtmlTest {
    final ResourceTemplateLoader resources = new ResourceTemplateLoader();

    @ParameterizedTest
    @ValueSource(strings = {"sports_cheer", "holiday_gift", "member_appreciation", "flash_sale", "pre_registration"})
    void reusableSnapshotKeepsThemeBlocksSlotsAndApprovedWidgets(String code) {
        String original = new TemplateService(resources).initialHtml(code);
        String filled = Slots.fill(original, "오래된 기간", "/old-event/participate");
        String cleaned = SavedTemplateHtml.clean(filled);
        var before = Jsoup.parseBodyFragment(original);
        var after = Jsoup.parseBodyFragment(cleaned);
        assertEquals(before.select("script").eachText(), after.select("script").eachText());
        assertEquals(before.select("script").stream().map(Element::data).toList(),
                after.select("script").stream().map(Element::data).toList());
        assertEquals(Slots.idsOf(original), Slots.idsOf(cleaned));
        assertEquals(Slots.keysOf(original), Slots.keysOf(cleaned));
        assertEquals(before.selectFirst(".ev-container").className(),
                after.selectFirst(".ev-container").className());
        assertEquals("", after.selectFirst(Slot.PERIOD.selector()).text());
        assertFalse(cleaned.contains("/old-event/participate"));
        assertFalse(after.selectFirst(Slot.CTA_LINK.selector()).text().isBlank());
        assertEquals(1, after.select(Block.NOTICES.selector()).size());
        assertFalse(cleaned.contains("<html"));
        assertEquals(cleaned, SavedTemplateHtml.clean(cleaned));
    }

    @Test
    void maliciousScriptsAndInlineHandlersAreNotCopied() {
        String html = new TemplateService(resources).initialHtml("sports_cheer")
                + "<script>alert('injected')</script><img src=x onerror='alert(1)'>";
        String cleaned = SavedTemplateHtml.clean(html);
        assertFalse(cleaned.contains("injected"));
        assertFalse(cleaned.contains("onerror"));
        assertFalse(cleaned.contains("javascript:"));
    }

    @Test
    void templateRegistrationCarriesParticipateLabel() {
        // 관리자가 자기 이벤트를 템플릿으로 등록한다. 이름표가 없는 조각을 받아도
        // 저장되는 템플릿에는 심긴 채로 들어가야 한다 — 이걸로 만든 이벤트가 자동으로 열린다
        String noLabel = "<section data-block=\"hero\"><h1>제목</h1></section>"
                + "<section data-block=\"cta\"><button class=\"cta-btn\">참여하기</button></section>";

        String cleaned = SavedTemplateHtml.clean(noLabel);

        assertEquals(1, Jsoup.parseBodyFragment(cleaned)
                .select("[data-behavior=participate]").size(),
                "등록된 템플릿에 참여 이름표가 없으면 이걸로 만든 이벤트도 참여가 안 열립니다");
    }

    @Test
    void approvedRootPaletteSurvivesReSanitizing() {
        String original = new TemplateService(resources).initialHtml("sports_cheer")
                .replace("theme-sports", "theme-sports palette-summer");
        assertTrue(Jsoup.parseBodyFragment(SavedTemplateHtml.clean(original))
                .selectFirst(".ev-container").hasClass("palette-summer"));
    }
}
