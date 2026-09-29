package com.newvent.event.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import com.newvent.event.dto.request.ButtonStyle;
import com.newvent.event.dto.request.TextEdit;
import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;
import com.newvent.registry.Block;

/**
 * 에디터 직접 편집 적용기.
 * <p>
 * 프론트엔드가 보낸 텍스트 치환 목록(TextEdit)과 CTA 버튼 스타일(ButtonStyle)을
 * 기준 버전 HTML에 직접 치환하여 새로운 HTML을 생성한다.
 * Jsoup의 파서 및 텍스트 치환을 사용하여 HTML/XSS 태그 삽입을 방지하고
 * data-slot·data-block·id 및 기존 문서 구조를 보존한다.
 */
public final class DirectEditor {

    private DirectEditor() {}

    /**
     * 원본 HTML에 텍스트 편집 및 버튼 스타일을 일괄 적용한다.
     *
     * @param baseHtml    기준 버전 HTML
     * @param edits       치환할 텍스트 목록 (null 가능)
     * @param buttonStyle 변경할 CTA 버튼 스타일 (null 가능)
     * @return 변경이 적용된 새 HTML
     */
    public static String apply(String baseHtml, List<TextEdit> edits, ButtonStyle buttonStyle) {
        String html = baseHtml;
        if (edits != null && !edits.isEmpty()) {
            html = applyTextEdits(html, edits);
        }
        if (buttonStyle != null) {
            html = applyButtonStyle(html, buttonStyle);
        }
        return html;
    }

    /**
     * [data-block] 내부 텍스트 노드를 문서 순서로 수집하여 지정된 문구로 치환한다.
     * <p>
     * 규칙:
     * 1. [data-block] 내부의 텍스트 노드를 문서 순서로 수집 (공백만 있는 노드 제외)
     * 2. data-block="notices" (서버 소유 블록)는 수집에서 제외
     * 3. index로 찾아 before와 대조, 불일치 시 409 Conflict 예외 발생
     * 4. TextNode.text(after)로 치환하여 자동 이스케이프 (XSS 방지)
     *
     * @param baseHtml 기준 버전 HTML
     * @param edits    치환할 텍스트 목록
     * @return 문구가 치환된 HTML
     */
    public static String applyTextEdits(String baseHtml, List<TextEdit> edits) {
        Objects.requireNonNull(baseHtml, "baseHtml은 필수입니다.");
        if (edits == null || edits.isEmpty()) {
            return baseHtml;
        }

        Document doc = Jsoup.parse(baseHtml);
        doc.outputSettings().prettyPrint(false);

        List<TextNode> textNodes = collectTextNodes(doc);

        for (TextEdit edit : edits) {
            int idx = edit.index();
            if (idx < 0 || idx >= textNodes.size()) {
                throw new DirectEditException(DirectEditErrorCode.INVALID_TEXT_INDEX);
            }

            TextNode targetNode = textNodes.get(idx);
            String actualText = targetNode.text().trim();
            String expectedText = edit.before().trim();

            if (!actualText.equals(expectedText) && !targetNode.getWholeText().trim().equals(expectedText)) {
                throw new DirectEditException(DirectEditErrorCode.BEFORE_TEXT_MISMATCH);
            }

            targetNode.text(edit.after());
        }

        return doc.outerHtml();
    }

    /**
     * CTA 참여 버튼([data-slot="cta-link"])의 인라인 style 속성에 지정된 스타일을 반영한다.
     * <p>
     * 규칙:
     * 1. [data-slot="cta-link"] 요소의 인라인 style 속성에 반영
     * 2. ButtonStyle 레코드의 값 화이트리스트 검증 통과 값만 반영
     * 3. null 필드는 건드리지 않음 (템플릿 기본값 유지)
     * 4. &lt;style&gt; 태그를 생성하지 않고 인라인 style로 적용
     *
     * @param baseHtml 기준 버전 HTML
     * @param style    적용할 버튼 스타일
     * @return 버튼 스타일이 적용된 HTML
     */
    public static String applyButtonStyle(String baseHtml, ButtonStyle style) {
        Objects.requireNonNull(baseHtml, "baseHtml은 필수입니다.");
        if (style == null) {
            return baseHtml;
        }

        Document doc = Jsoup.parse(baseHtml);
        doc.outputSettings().prettyPrint(false);

        Element ctaElement = doc.selectFirst("[data-slot=\"cta-link\"]");
        if (ctaElement == null) {
            throw new DirectEditException(DirectEditErrorCode.CTA_NOT_FOUND);
        }

        Map<String, String> styleMap = parseInlineStyle(ctaElement.attr("style"));

        if (style.background() != null) {
            styleMap.put("background-color", style.background());
            styleMap.put("background", style.background());
        }
        if (style.color() != null) {
            styleMap.put("color", style.color());
        }
        if (style.size() != null) {
            applySizeStyle(styleMap, style.size());
        }
        if (style.shape() != null) {
            applyShapeStyle(styleMap, style.shape());
        }

        String serializedStyle = serializeInlineStyle(styleMap);
        if (serializedStyle.isEmpty()) {
            ctaElement.removeAttr("style");
        } else {
            ctaElement.attr("style", serializedStyle);
        }

        return doc.outerHtml();
    }

    private static void applySizeStyle(Map<String, String> styleMap, String size) {
        switch (size) {
            case "small" -> {
                styleMap.put("font-size", "14px");
                styleMap.put("padding", "10px 20px");
                styleMap.put("min-height", "40px");
            }
            case "medium" -> {
                styleMap.put("font-size", "18px");
                styleMap.put("padding", "16px 28px");
                styleMap.put("min-height", "52px");
            }
            case "large" -> {
                styleMap.put("font-size", "22px");
                styleMap.put("padding", "22px 32px");
                styleMap.put("min-height", "64px");
            }
            default -> throw new IllegalArgumentException("지원하지 않는 size입니다: " + size);
        }
    }

    private static void applyShapeStyle(Map<String, String> styleMap, String shape) {
        switch (shape) {
            case "square" -> styleMap.put("border-radius", "0");
            case "round" -> styleMap.put("border-radius", "12px");
            case "pill" -> styleMap.put("border-radius", "999px");
            default -> throw new IllegalArgumentException("지원하지 않는 shape입니다: " + shape);
        }
    }

    private static List<TextNode> collectTextNodes(Document doc) {
        List<TextNode> result = new ArrayList<>();
        Element body = doc.body();
        if (body == null) {
            return result;
        }

        NodeTraversor.traverse(new NodeVisitor() {
            private int noticeDepth = 0;
            private int blockDepth = 0;

            @Override
            public void head(Node node, int depth) {
                if (node instanceof Element el) {
                    if (el.hasAttr("data-block")) {
                        blockDepth++;
                        if (el.is(Block.NOTICES.selector())) {
                            noticeDepth++;
                        }
                    }
                } else if (node instanceof TextNode tn) {
                    if (blockDepth > 0 && noticeDepth == 0) {
                        if (!isIgnoredParent(tn) && !tn.isBlank()) {
                            result.add(tn);
                        }
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element el) {
                    if (el.hasAttr("data-block")) {
                        if (el.is(Block.NOTICES.selector())) {
                            noticeDepth--;
                        }
                        blockDepth--;
                    }
                }
            }
        }, body);

        return result;
    }

    private static boolean isIgnoredParent(TextNode tn) {
        Node parent = tn.parent();
        if (parent instanceof Element el) {
            String tag = el.normalName();
            return tag.equals("script") || tag.equals("style") || tag.equals("textarea") || tag.equals("select");
        }
        return false;
    }

    private static Map<String, String> parseInlineStyle(String styleAttr) {
        Map<String, String> map = new LinkedHashMap<>();
        if (styleAttr == null || styleAttr.isBlank()) {
            return map;
        }
        for (String decl : styleAttr.split(";")) {
            int colonIdx = decl.indexOf(':');
            if (colonIdx > 0) {
                String prop = decl.substring(0, colonIdx).trim().toLowerCase();
                String val = decl.substring(colonIdx + 1).trim();
                if (!prop.isEmpty() && !val.isEmpty()) {
                    map.put(prop, val);
                }
            }
        }
        return map;
    }

    private static String serializeInlineStyle(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("; ");
        }
        return sb.toString().trim();
    }
}
