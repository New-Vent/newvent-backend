package com.newvent.event.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.event.dto.request.ButtonStyle;
import com.newvent.event.dto.request.TextEdit;
import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;
import com.newvent.generation.service.ResourceTemplateLoader;
import com.newvent.generation.service.TemplateLoader;
import com.newvent.registry.Block;

class DirectEditorTest {

    private static final TemplateLoader TEMPLATES = new ResourceTemplateLoader();

    @Test
    @DisplayName("템플릿 5종 전체에서 data-block 내부 텍스트는 수집되고 notices 블록은 제외된다")
    void 템플릿5종_텍스트노드_수집_검증() {
        assertEquals(5, TEMPLATES.all().size());

        for (TemplateLoader.Source template : TEMPLATES.all()) {
            String html = template.html();
            Document doc = Jsoup.parse(html);
            Element noticesEl = doc.selectFirst(Block.NOTICES.selector());
            assertNotNull(noticesEl, template.code() + " 에 notices 블록이 없습니다.");

            String noticeText = noticesEl.text();
            assertTrue(noticeText.contains("유의사항") || noticeText.contains("확인"),
                    template.code() + " notices 텍스트가 비정상적입니다.");

            // notices의 텍스트로 치환 시도 시 모두 실패해야 함 (notices는 수집 목록에 없으므로)
            List<TextEdit> invalidEdits = List.of(new TextEdit(0, "유의사항", "변조 시도"));
            DirectEditException ex = assertThrows(DirectEditException.class,
                    () -> DirectEditor.applyTextEdits(html, invalidEdits));
            assertEquals(DirectEditErrorCode.BEFORE_TEXT_MISMATCH, ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("단일 문구 치환 시 지정한 자리만 정확히 바뀌고 XSS 태그는 이스케이프된다")
    void 문구치환_및_XSS방지() {
        TemplateLoader.Source tpl = TEMPLATES.find("template_1_sports_cheer").orElseThrow();
        String html = tpl.html();

        // 템플릿 1의 첫 문구는 HERO의 배지 문구 "LIVE PROMOTION"
        String edited = DirectEditor.applyTextEdits(html, List.of(
                new TextEdit(0, "LIVE PROMOTION", "<script>alert('xss')</script>실시간 이벤트")));

        assertFalse(edited.contains("<script>alert('xss')</script>"), "HTML 태그가 그대로 삽입되면 안 됩니다.");
        assertTrue(edited.contains("&lt;script&gt;alert('xss')&lt;/script&gt;실시간 이벤트"),
                "태그가 안전하게 엔티티로 이스케이프되어야 합니다.");

        // 기존 구조 보존 확인
        Document doc = Jsoup.parse(edited);
        assertNotNull(doc.selectFirst("[data-block=\"hero\"]"));
        assertNotNull(doc.selectFirst("[data-slot=\"period\"]"));
        assertNotNull(doc.selectFirst("[data-slot=\"cta-link\"]"));
        assertNotNull(doc.selectFirst(Block.NOTICES.selector()));
    }

    @Test
    @DisplayName("before 문구가 불일치하거나 인덱스가 범위를 벗어나면 예외가 발생한다")
    void 불일치_및_범위초과_예외() {
        TemplateLoader.Source tpl = TEMPLATES.find("template_1_sports_cheer").orElseThrow();
        String html = tpl.html();

        // before 불일치 -> 409 Conflict
        DirectEditException mismatchEx = assertThrows(DirectEditException.class,
                () -> DirectEditor.applyTextEdits(html, List.of(new TextEdit(0, "완전히 다른 문구", "새 문구"))));
        assertEquals(DirectEditErrorCode.BEFORE_TEXT_MISMATCH, mismatchEx.getErrorCode());

        // 범위 초과 인덱스 -> 400 Bad Request
        DirectEditException outOfBoundsEx = assertThrows(DirectEditException.class,
                () -> DirectEditor.applyTextEdits(html, List.of(new TextEdit(9999, "아무거나", "새 문구"))));
        assertEquals(DirectEditErrorCode.INVALID_TEXT_INDEX, outOfBoundsEx.getErrorCode());
    }

    @Test
    @DisplayName("template_5의 중첩 컨테이너(.benefits-list in .lc-gauge-card)에서도 정상 치환된다")
    void template5_중첩구조_정상치환() {
        TemplateLoader.Source tpl = TEMPLATES.find("template_5_pre_registration").orElseThrow();
        String html = tpl.html();

        // template_5의 게이지 달성 관련 텍스트 노드 치환
        String edited = DirectEditor.applyTextEdits(html, List.of(
                new TextEdit(0, "NEXT-GEN AI PLATFORM", "NEW GENERATION PLATFORM")));

        assertTrue(edited.contains("NEW GENERATION PLATFORM"));
        Document doc = Jsoup.parse(edited);
        assertNotNull(doc.selectFirst(".lc-gauge-card"));
        assertNotNull(doc.selectFirst(".benefits-list"));
        assertNotNull(doc.selectFirst("[data-slot=\"cta-link\"]"));
    }

    @Test
    @DisplayName("CTA 버튼 인라인 스타일이 올바르게 반영되고 null 필드는 기본값을 유지한다")
    void 버튼스타일_인라인적용() {
        TemplateLoader.Source tpl = TEMPLATES.find("template_1_sports_cheer").orElseThrow();
        String html = tpl.html();

        ButtonStyle style = new ButtonStyle("#d60076", "#ffffff", "medium", "pill");
        String edited = DirectEditor.applyButtonStyle(html, style);

        Document doc = Jsoup.parse(edited);
        Element cta = doc.selectFirst("[data-slot=\"cta-link\"]");
        assertNotNull(cta);

        String styleAttr = cta.attr("style");
        assertTrue(styleAttr.contains("background-color: #d60076"));
        assertTrue(styleAttr.contains("color: #ffffff"));
        assertTrue(styleAttr.contains("font-size: 18px"));
        assertTrue(styleAttr.contains("padding: 16px 28px"));
        assertTrue(styleAttr.contains("border-radius: 999px"));

        // style 태그가 신규 생성되지 않았는지 확인
        assertNull(doc.selectFirst("#nv-button-style"), "임의의 <style> 태그가 생기면 안 됩니다.");
    }

    @Test
    @DisplayName("ButtonStyle 화이트리스트 외의 값이 들어오면 예외를 발생시킨다")
    void 버튼스타일_화이트리스트_예외() {
        // 잘못된 색상 형식
        DirectEditException ex1 = assertThrows(DirectEditException.class,
                () -> new ButtonStyle("red", "#ffffff", "medium", "pill"));
        assertEquals(DirectEditErrorCode.INVALID_BUTTON_STYLE, ex1.getErrorCode());

        DirectEditException ex2 = assertThrows(DirectEditException.class,
                () -> new ButtonStyle("#12345", "#ffffff", "medium", "pill"));
        assertEquals(DirectEditErrorCode.INVALID_BUTTON_STYLE, ex2.getErrorCode());

        // 잘못된 size
        DirectEditException ex3 = assertThrows(DirectEditException.class,
                () -> new ButtonStyle("#000000", "#ffffff", "extra-large", "pill"));
        assertEquals(DirectEditErrorCode.INVALID_BUTTON_STYLE, ex3.getErrorCode());

        // 잘못된 shape
        DirectEditException ex4 = assertThrows(DirectEditException.class,
                () -> new ButtonStyle("#000000", "#ffffff", "medium", "triangle"));
        assertEquals(DirectEditErrorCode.INVALID_BUTTON_STYLE, ex4.getErrorCode());
    }

    @Test
    @DisplayName("apply 메서드로 텍스트 치환과 버튼 스타일을 동시에 적용할 수 있다")
    void 텍스트치환과_버튼스타일_동시적용() {
        TemplateLoader.Source tpl = TEMPLATES.find("template_3_member_appreciation").orElseThrow();
        String html = tpl.html();

        List<TextEdit> edits = List.of(new TextEdit(0, "VIP & FAMILY ONLY", "ALL MEMBERS WELCOME"));
        ButtonStyle buttonStyle = new ButtonStyle("#2563eb", null, "small", "round");

        String result = DirectEditor.apply(html, edits, buttonStyle);

        assertTrue(result.contains("ALL MEMBERS WELCOME"));
        Document doc = Jsoup.parse(result);
        Element cta = doc.selectFirst("[data-slot=\"cta-link\"]");
        String styleAttr = cta.attr("style");
        assertTrue(styleAttr.contains("background-color: #2563eb"));
        assertTrue(styleAttr.contains("border-radius: 12px"));
        assertTrue(styleAttr.contains("font-size: 14px"));
        assertFalse(styleAttr.matches("(?s).*\\b(?<!background-)color:\\s*[^;]+.*"),
                "null인 color는 인라인에 들어가지 않아야 합니다.");
    }
}
