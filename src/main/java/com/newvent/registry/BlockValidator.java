package com.newvent.registry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * 레지스트리로 생성 결과를 검증한다.
 *
 * ★ 여기에 "h1 이 있나" 같은 걸 직접 쓰지 마세요.
 *   전부 Block.must() 에서 읽어옵니다. 블록을 추가하면 검증도 자동으로 따라옵니다.
 *
 * 벤치마크 v8.4 의 check_html() 을 옮긴 것입니다.
 * 실패 메시지는 재시도 프롬프트에 그대로 들어갑니다 —
 * "JSON 파싱 실패" 같은 문법 메시지는 모델이 못 고치고,
 * "hero 영역이 없습니다" 같은 의미 메시지라야 고칩니다.
 */
public class BlockValidator {

    public record Failure(String code, String message) {}

    /** 모델 출력에서 HTML 만 뽑는다. 코드펜스가 45% 확률로 붙어서 온다. */
    public static String extract(String raw) {
        String t = raw.replace("```html", "").replace("```", "");
        int s = t.indexOf('<'), e = t.lastIndexOf('>');
        return (s >= 0 && e > s) ? t.substring(s, e + 1).trim() : "";
    }

    /** 생성 결과 검증 — 필수 블록이 다 있고 형태가 맞는가 */
    public static List<Failure> validateGenerated(String html) {
        List<Failure> f = new ArrayList<>();
        if (html == null || html.isBlank()) {
            f.add(new Failure("no_html", "HTML을 찾을 수 없습니다. <section> 으로 시작하는 HTML만 출력하세요."));
            return f;
        }
        Document doc = Jsoup.parseBodyFragment(html);

        if (doc.body().select("section").isEmpty()) {
            f.add(new Failure("no_section", "<section> 태그가 없습니다."));
        }
        for (Element sec : doc.body().select("section")) {
            if (!sec.hasAttr("data-block")) {
                f.add(new Failure("no_data_block",
                        "모든 <section> 에 data-block=\"이름\" 속성이 있어야 합니다."));
                break;
            }
        }

        for (Block b : Block.llmBlocks()) {
            Element el = doc.body().selectFirst(b.selector());
            if (el == null) {
                if (b.required()) {
                    f.add(new Failure("lost_" + b.key(), b.key() + " 영역이 없습니다."));
                }
                continue;
            }
            checkShape(b, el, f);
        }

        // 서버 소유 블록을 모델이 만들었나
        for (Block b : Block.serverBlocks()) {
            if (doc.body().selectFirst(b.selector()) != null) {
                f.add(new Failure("wrote_" + b.key(),
                        b.key() + " 영역은 만들면 안 됩니다. 서버가 채웁니다."));
            }
        }

        if (html.matches("(?s).*\\[[가-힣A-Za-z0-9 _\\-]{1,15}\\].*")) {
            f.add(new Failure("placeholder", "자리표시자가 남아 있습니다. 해당 문장을 빼세요."));
        }
        return f;
    }

    /**
     * 수정 결과 검증 — 그 블록만 왔는가, 그리고 건드리면 안 되는 게 그대로인가.
     *
     * ★ before 가 필요한 이유
     *   수정은 "원본 대비" 로만 판정할 수 있는 항목이 있습니다. 슬롯이 그렇습니다.
     *   원본에 있던 [data-slot] 이 없어졌는지는 원본을 봐야 압니다.
     *
     * @param before 수정 전 그 블록의 HTML (서버가 갖고 있는 것)
     * @param html   모델이 돌려준 것 (sanitizeEdited 를 먼저 거친 것)
     */
    public static List<Failure> validateEdited(Block target, String before, String html) {
        List<Failure> f = new ArrayList<>();
        Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);

        Element el = doc.body().selectFirst(target.selector());
        if (el == null) {
            f.add(new Failure("lost_" + target.key(), target.key() + " 영역이 없습니다."));
            return f;
        }
        for (Block b : Block.values()) {
            if (b != target && doc.body().selectFirst(b.selector()) != null) {
                f.add(new Failure("extra_" + b.key(),
                        b.key() + " 영역은 만들지 마세요. 요청한 영역만 출력하세요."));
            }
        }
        checkShape(target, el, f);
        checkSlots(before, html, f);
        return f;
    }

    /**
     * 블록 하나가 제 모양을 갖췄나.
     *
     * ★ 생성·수정이 같은 규칙을 쓰게 하는 지점입니다.
     *   두 곳에 따로 적으면 어긋납니다 — 실제로 minItems 가 수정 쪽에만 빠져 있었고,
     *   그래서 "생성으로는 못 만드는 상태를 수정으로는 만들 수 있는" 구멍이 있었습니다.
     */
    private static void checkShape(Block b, Element el, List<Failure> f) {
        if (b.must() == null) return;

        if (el.selectFirst(b.must()) == null) {
            f.add(new Failure("empty_" + b.key(),
                    b.key() + " 안에 " + b.shape() + " — 맨 텍스트만 두면 안 됩니다."));
            return;
        }
        if (b.minItems() > 0) {
            // ★ li 를 하드코딩하지 않는다. must() 가 곧 세는 기준이다.
            int n = el.select(b.must()).size();
            if (n < b.minItems()) {
                f.add(new Failure("few_" + b.key(),
                        b.key() + " 항목이 " + n + "개입니다. " + b.minItems() + "개 이상 필요합니다."));
            }
        }
    }

    // ── 슬롯 보존 ──────────────────────────────────────────────────

    /** 이 HTML 안에 있는 data-slot 키들 */
    private static Set<String> slotsOf(String html) {
        Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);
        Set<String> keys = new LinkedHashSet<>();
        for (Element el : doc.body().select("[data-slot]")) {
            String k = el.attr("data-slot").trim();
            if (!k.isEmpty()) keys.add(k);
        }
        return keys;
    }

    /**
     * 슬롯 집합이 원본과 같은가.
     *
     * ★ 수정에서 슬롯이 사라지면 그 이벤트는 영구히 그 값을 못 채웁니다.
     *   merge 가 블록을 통째로 갈아끼우기 때문에, 한 번 없어지면 복구할 자리가 없습니다.
     *   지우는 것도 새로 만드는 것도 둘 다 실패입니다.
     */
    private static void checkSlots(String before, String after, List<Failure> f) {
        Set<String> was = slotsOf(before);
        Set<String> now = slotsOf(after);

        for (String key : was) {
            if (!now.contains(key)) {
                f.add(new Failure("slot_lost_" + key,
                        "data-slot=\"" + key + "\" 가 붙은 태그를 지웠습니다. "
                                + "그 자리는 서버가 채우는 곳입니다. 태그와 속성을 원래대로 두세요."));
            }
        }
        for (String key : now) {
            if (!was.contains(key)) {
                f.add(new Failure("slot_invented_" + key,
                        "data-slot=\"" + key + "\" 를 새로 만들었습니다. "
                                + "data-slot 은 서버만 심습니다. 원래 있던 것만 그대로 두세요."));
            }
        }
    }

    // ── 정화 ──────────────────────────────────────────────────────

    /**
     * style 속성에 허용할 CSS 속성. **허용 목록이다. 금지 목록이 아니다.**
     * 채팅으로 하는 수정이 "색 바꿔줘 / 글씨 키워줘" 수준이라 이 정도면 충분하다.
     *
     * position · z-index · transform 을 빼둔 이유:
     *   화면을 덮는 요소를 만들 수 있다. 검토 안 된 출력에 허용할 이유가 없다.
     */
    private static final Set<String> ALLOWED_CSS = Set.of(
            "color", "background-color",
            "font-size", "font-weight", "font-style", "line-height",
            "text-align", "text-decoration",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
            "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
            "border", "border-color", "border-width", "border-style", "border-radius");

    /**
     * 생성 결과 정화 — data-slot 을 **지운다**.
     *
     * 빈 문서에서 만드는 것이라 슬롯이 있을 이유가 없다.
     * 모델이 data-slot 을 만들어내면 서버의 쓰기 지점을 모델이 만드는 셈이 된다.
     */
    public static String sanitizeGenerated(String html) {
        return clean(html, false);
    }

    /**
     * 수정 결과 정화 — data-slot 을 **남긴다**.
     *
     * ★ 생성과 반대다. 원본에 이미 있던 슬롯을 보존해야 하기 때문이다.
     *   대신 모델이 지어낸 슬롯은 validateEdited 의 checkSlots 가 잡는다.
     *   "지우기" 와 "대조하기" 중 하나만 있으면 안 된다 — 둘이 한 쌍이다.
     */
    public static String sanitizeEdited(String html) {
        return clean(html, true);
    }

    /**
     * ★ 이 함수는 "모델이 준 조각" 에만 건다.
     *   서버가 조립한 최종 문서나 템플릿에 걸면 템플릿의 button·script 가 다 죽는다.
     *   순서:  sanitize(모델 출력) → merge   (반대로 하지 말 것)
     */
    private static String clean(String html, boolean keepSlots) {
        Safelist s = Safelist.relaxed()
                .addAttributes(":all", "style", "data-block", "class")
                .addTags("section")
        		.addProtocols("a", "href", "#");
        if (keepSlots) {
            s = s.addAttributes(":all", "data-slot");
        }
        String cleaned = Jsoup.clean(html, "", s, new Document.OutputSettings().prettyPrint(false));

        // ★ Jsoup 은 style 속성이 "있는지"만 보고 "값"은 보지 않는다. 값은 여기서 거른다.
        Document doc = Jsoup.parseBodyFragment(cleaned);
        doc.outputSettings().prettyPrint(false);
        for (Element el : doc.body().select("[style]")) {
            String safe = cleanStyle(el.attr("style"));
            if (safe.isEmpty()) el.removeAttr("style");
            else el.attr("style", safe);
        }
        return doc.body().html();
    }

    /** style="..." 안에서 허용 목록에 있는 선언만 남긴다 */
    private static String cleanStyle(String style) {
        StringBuilder out = new StringBuilder();
        for (String decl : style.split(";")) {
            int i = decl.indexOf(':');
            if (i < 0) continue;

            String prop = decl.substring(0, i).trim().toLowerCase();
            String val  = decl.substring(i + 1).trim();
            if (val.isEmpty() || !ALLOWED_CSS.contains(prop)) continue;

            // 허용 속성이어도 값 안에 숨을 수 있는 것들
            String low = val.toLowerCase();
            if (low.contains("url(") || low.contains("expression(")
                    || low.contains("javascript:") || low.contains("@import")
                    || low.contains("/*") || low.contains("\\")) continue;

            out.append(prop).append(':').append(val).append(';');
        }
        return out.toString();
    }

    /**
     * 모델이 뭘 돌려주든 그 블록 자리에만 끼워 넣는다.
     * 실험에서 모델이 "조각만" 또는 "전체를" 돌려주는 게 제각각이었고
     * 프롬프트로 통제되지 않았습니다. 서버가 판별해서 병합합니다.
     */
    public static String merge(String currentDoc, Block target, String modelOutput) {
        Document cur = Jsoup.parseBodyFragment(currentDoc);
        Document out = Jsoup.parseBodyFragment(modelOutput);

        Element incoming = out.body().selectFirst(target.selector());
        if (incoming == null) return currentDoc;      // 대상 블록이 없으면 원본 유지

        Element old = cur.body().selectFirst(target.selector());
        if (old == null) cur.body().appendChild(incoming);
        else old.replaceWith(incoming);

        return cur.body().html();
    }

    /** 문서에서 블록 하나만 떼어낸다 — 수정 요청과 validateEdited 의 before 로 쓴다 */
    public static String blockOf(String doc, Block target) {
        Element el = Jsoup.parseBodyFragment(doc).body().selectFirst(target.selector());
        return el == null ? "" : el.outerHtml();
    }
}
