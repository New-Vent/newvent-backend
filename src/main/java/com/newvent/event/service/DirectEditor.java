package com.newvent.event.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import com.newvent.event.dto.request.ButtonStyle;
import com.newvent.event.dto.request.DirectEditRequest.ItemRemoval;
import com.newvent.event.dto.request.TextEdit;
import com.newvent.event.dto.request.TextFormat;
import com.newvent.event.dto.request.TextStyle;
import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;
import com.newvent.registry.Block;
import com.newvent.registry.Slot;

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
    private static final Set<Block> ADMIN_KEEP = EnumSet.of(Block.HERO, Block.NOTICES, Block.CTA);
    private static final String TEXT_ID_PREFIX = "nv-direct-text-";
    private static boolean marked(TextNode node) {
        return node.parent() instanceof Element el && el.normalName().equals("span")
                && el.id().startsWith(TEXT_ID_PREFIX);
    }
    private static String editableValue(TextNode node) {
        return marked(node) ? node.getWholeText() : node.text().trim();
    }

    /** 미리보기 HTML과 저장본에 공통으로 적용하는 편집 가능 문구 순서. */
    public record EditableText(int index, String before, Map<String, String> format) {
        public EditableText(int index, String before) {
            this(index, before, Map.of());
        }
    }

    public static List<EditableText> editableTexts(String html) {
        Objects.requireNonNull(html, "html은 필수입니다.");
        List<TextNode> nodes = collectTextNodes(Jsoup.parseBodyFragment(html));
        List<EditableText> result = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            TextNode node = nodes.get(i);
            Map<String, String> format = new LinkedHashMap<>();
            if (marked(node)) {
                Map<String, String> styles = parseInlineStyle(((Element) node.parent()).attr("style"));
                for (String key : List.of("color", "font-size", "font-weight")) {
                    if (styles.containsKey(key)) format.put(key, styles.get(key));
                }
            }
            result.add(new EditableText(i, editableValue(node), Map.copyOf(format)));
        }
        return List.copyOf(result);
    }

    /**
     * 원본 HTML에 텍스트 편집 및 버튼 스타일을 일괄 적용한다.
     *
     * @param baseHtml    기준 버전 HTML
     * @param edits       치환할 텍스트 목록 (null 가능)
     * @param buttonStyle 변경할 CTA 버튼 스타일 (null 가능)
     * @return 변경이 적용된 새 HTML
     */
    public static String apply(String baseHtml, List<TextEdit> edits, ButtonStyle buttonStyle) {
        return apply(baseHtml, edits, buttonStyle, null);
    }

    public static String apply(String baseHtml, List<TextEdit> edits,
                               ButtonStyle buttonStyle, TextStyle textStyle) {
        String html = baseHtml;
        if (edits != null && !edits.isEmpty()) {
            html = applyTextEdits(html, edits);
        }
        if (buttonStyle != null) {
            html = applyButtonStyle(html, buttonStyle);
        }
        if (textStyle != null && !textStyle.isEmpty()) {
            html = applyTextStyle(html, textStyle);
        }
        return html;
    }

    /** 관리자 직접수정에서 블록 또는 레지스트리 항목을 삭제한다. */
    public static String applyDeletions(String html, List<String> removeBlocks,
                                        Map<String, List<ItemRemoval>> removeItems) {
        if ((removeBlocks == null || removeBlocks.isEmpty())
                && (removeItems == null || removeItems.isEmpty())) return html;
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        Set<String> removing = new LinkedHashSet<>(removeBlocks == null ? List.of() : removeBlocks);
        for (String key : removing) {
            Block block = Block.of(key);
            if (ADMIN_KEEP.contains(block))
                throw new DirectEditException(DirectEditErrorCode.BLOCK_DELETE_FORBIDDEN,
                        block.key() + " 영역은 직접수정에서 지울 수 없습니다.");
        }
        if (removeItems != null) for (var entry : removeItems.entrySet()) {
            Block block = Block.of(entry.getKey());
            if (removing.contains(block.key()))
                throw new DirectEditException(DirectEditErrorCode.ITEM_DELETE_MISMATCH, "같은 요청에서 블록과 그 항목을 함께 지울 수 없습니다.");
            Element section = doc.selectFirst(block.selector());
            if (section == null || block.must() == null)
                throw new DirectEditException(DirectEditErrorCode.ITEM_DELETE_MISMATCH);
            List<Element> items = new ArrayList<>(section.select(block.must()).stream().distinct().toList());
            List<ItemRemoval> removals = entry.getValue().stream().sorted((a, b) -> Integer.compare(b.index(), a.index())).toList();
            for (ItemRemoval removal : removals) {
                if (removal.index() >= items.size()) throw new DirectEditException(DirectEditErrorCode.ITEM_DELETE_MISMATCH);
                // sourceVersionId 가 가리키는 HTML은 불변이므로, 이 버전의 항목 순번이 안정적인 기준이다.
                // 브라우저 textContent 와 Jsoup 텍스트 추출은 구조 공백 처리 차이로 달라질 수 있어
                // 문구 비교를 인덱스 검증의 추가 조건으로 삼지 않는다.
                items.get(removal.index()).remove();
                items.set(removal.index(), null);
            }
            int remaining = section.select(block.must()).size();
            if (remaining < block.minItems())
                throw new DirectEditException(DirectEditErrorCode.TOO_FEW_ITEMS,
                        block.key() + "은 항목이 " + block.minItems() + "개 이상 있어야 합니다. 지금은 " + remaining + "개가 남습니다.");
        }
        for (String key : removing) {
            Element target = doc.selectFirst(Block.of(key).selector());
            if (target != null) target.remove();
        }
        return doc.body().html();
    }

    /**
     * 제목 글자 크기 · 색을 인라인 style 로 박는다.
     *
     * ★★ 왜 인라인인가 — CSS 를 이기는 길이 이것뿐이다. 테마가 0,4,1 로 제목을 집고 있어서
     *   class 로는 못 이긴다(95-assets · 20-themes 에서 겪은 그대로).
     *   인라인은 특이도 싸움 밖이라 무조건 이긴다.
     *
     * ★ 비운 항목은 **손대지 않는다.** 비움이 "테마를 따른다" 는 뜻이다 —
     *   전부 박아 버리면 팔레트를 바꿔도 제목만 옛 색으로 남는다.
     *
     * ★ 큰 제목과 소제목을 갈라 받는다. 한 값으로 묶으면 둘의 크기 차이(위계)가 사라진다 —
     *   "제목이 밋밋하다" 가 거기서 나온다.
     *
     * ★ 제목이 없는 페이지에서도 터지지 않는다. 버튼(CTA_NOT_FOUND)과 다르다 —
     *   버튼은 반드시 있어야 하지만 소제목은 없을 수 있다.
     */
    public static String applyTextStyle(String baseHtml, TextStyle style) {
        Objects.requireNonNull(baseHtml, "baseHtml은 필수입니다.");
        if (style == null || style.isEmpty()) {
            return baseHtml;
        }

        Document doc = Jsoup.parseBodyFragment(baseHtml);
        doc.outputSettings().prettyPrint(false);

        for (Element el : doc.select("[data-block=\"hero\"] h1")) {
            paint(el, style.titleColor(), style.titleSize(), TITLE_SIZES);
        }
        // ★ hero 안의 h2 는 뺀다. 거기 h2 가 있으면 그건 큰 제목의 보조라 소제목이 아니다
        for (Element el : doc.select("[data-block] h2, [data-block] .block-header .title")) {
            if (el.closest("[data-block=\"hero\"]") != null) continue;
            paint(el, style.headingColor(), style.headingSize(), HEADING_SIZES);
        }
        return doc.body().html();
    }

    /** 큰 제목 크기표 — rem 이 아니라 px 다. 인라인은 테마의 clamp() 를 통째로 덮는다 */
    private static final Map<String, String> TITLE_SIZES = Map.of(
            "small", "28px", "medium", "36px", "large", "46px", "xlarge", "58px");

    private static final Map<String, String> HEADING_SIZES = Map.of(
            "small", "18px", "medium", "22px", "large", "27px", "xlarge", "32px");

    private static void paint(Element el, String color, String size, Map<String, String> table) {
        Map<String, String> styleMap = parseInlineStyle(el.attr("style"));
        if (color != null) {
            styleMap.put("color", color);
            // ★★ -webkit-text-fill-color 도 같이 박는다. 테마 · 변형이 글자를 투명하게 두고
            //   배경을 깎아 넣는 수법을 쓰는 데가 있어서(v-hero-gradient-text),
            //   color 만 바꾸면 **아무 일도 안 일어난다**. 95-assets 에서 같은 함정을 겪었다.
            styleMap.put("-webkit-text-fill-color", color);
        }
        if (size != null) {
            String px = table.get(size);
            if (px != null) styleMap.put("font-size", px);
        }
        String serialized = serializeInlineStyle(styleMap);
        if (serialized.isEmpty()) {
            el.removeAttr("style");
        } else {
            el.attr("style", serialized);
        }
    }

    /**
     * [data-block] 내부 텍스트 노드를 문서 순서로 수집하여 지정된 문구로 치환한다.
     * <p>
     * 규칙:
     * 1. [data-block] 내부의 텍스트 노드를 문서 순서로 수집 (공백만 있는 노드 제외)
     * 2. 유의사항은 관리자 직접 편집을 허용하고, 서버가 채우는 period 슬롯은 제외
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

        Document doc = Jsoup.parseBodyFragment(baseHtml);
        doc.outputSettings().prettyPrint(false);

        List<TextNode> textNodes = collectTextNodes(doc);
        var indexes = new HashSet<Integer>();

        for (TextEdit edit : edits) {
            int idx = edit.index();
            if (!indexes.add(idx)) throw new DirectEditException(DirectEditErrorCode.DUPLICATE_TEXT_INDEX);
            if (idx < 0 || idx >= textNodes.size()) {
                throw new DirectEditException(DirectEditErrorCode.INVALID_TEXT_INDEX);
            }

            TextNode targetNode = textNodes.get(idx);
            String actualText = editableValue(targetNode);
            String expectedText = marked(targetNode) ? edit.before() : edit.before().trim();

            if (!actualText.equals(expectedText)
                    && (marked(targetNode) || !targetNode.getWholeText().trim().equals(expectedText))) {
                throw new DirectEditException(DirectEditErrorCode.BEFORE_TEXT_MISMATCH);
            }

        }

        // 원본 인덱스로 모든 항목을 검증한 뒤 문구와 서식을 변경한다.
        for (TextEdit edit : edits) {
            int idx = edit.index();
            TextNode targetNode = textNodes.get(idx);

            if (!marked(targetNode)) {
                String id = TEXT_ID_PREFIX + idx;
                while (doc.getElementById(id) != null) id += "-next";
                Element holder = new Element("span").attr("id", id)
                        .attr("style", "white-space: pre-line;");
                targetNode.replaceWith(holder);
                holder.appendChild(targetNode);
            }
            targetNode.text(edit.after().replace("\r\n", "\n").replace('\r', '\n'));
            applyFormat((Element) targetNode.parent(), edit.format());
        }

        return doc.body().html();
    }

    private static void applyFormat(Element holder, TextFormat format) {
        if (format == null) return;
        Map<String, String> styles = parseInlineStyle(holder.attr("style"));
        if (format.reset()) {
            for (String key : List.of("color", "-webkit-text-fill-color", "font-size", "font-weight")) styles.remove(key);
        }
        if (format.color() != null) {
            styles.put("color", format.color());
            styles.put("-webkit-text-fill-color", format.color());
        }
        if (format.size() != null) styles.put("font-size", format.size() + "px");
        if (format.bold() != null) styles.put("font-weight", format.bold() ? "700" : "400");
        holder.attr("style", serializeInlineStyle(styles));
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

        Document doc = Jsoup.parseBodyFragment(baseHtml);
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

        return doc.body().html();
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
        // 빈 텍스트 노드는 다시 읽으면 사라지므로, 남아 있는 문구 요소 안에 복원한다.
        for (Element el : doc.select("span[id^=" + TEXT_ID_PREFIX + "]")) {
            if (el.childNodeSize() == 0) el.appendChild(new TextNode(""));
        }
        List<TextNode> result = new ArrayList<>();
        Element body = doc.body();
        if (body == null) {
            return result;
        }

        NodeTraversor.traverse(new NodeVisitor() {
            private int periodDepth = 0;
            private int blockDepth = 0;

            @Override
            public void head(Node node, int depth) {
                if (node instanceof Element el) {
                    if (el.is(Slot.PERIOD.selector())) {
                        periodDepth++;
                    }
                    if (el.hasAttr("data-block")) {
                        blockDepth++;
                    }
                } else if (node instanceof TextNode tn) {
                    if (blockDepth > 0 && periodDepth == 0) {
                        if (!isIgnoredParent(tn) && (!tn.isBlank() || marked(tn))) {
                            result.add(tn);
                        }
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element el) {
                    if (el.is(Slot.PERIOD.selector())) {
                        periodDepth--;
                    }
                    if (el.hasAttr("data-block")) {
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
