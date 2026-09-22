package com.newvent.registry;

import java.util.ArrayList;
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
            checkShape(b, el, f, false);
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
     *   수정은 "원본 대비" 로만 판정할 수 있는 항목이 있습니다.
     *   원본에 있던 [data-slot] 이나 id 가 없어졌는지는 원본을 봐야 압니다.
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
        checkShape(target, el, f, true);
        checkPreserved(before, html, f);
        return f;
    }

    /**
     * 블록 하나가 제 모양을 갖췄나.
     *
     * ★ 생성·수정이 같은 규칙을 쓰게 하는 지점입니다.
     *   두 곳에 따로 적으면 어긋납니다 — 실제로 minItems 가 수정 쪽에만 빠져 있었고,
     *   그래서 "생성으로는 못 만드는 상태를 수정으로는 만들 수 있는" 구멍이 있었습니다.
     *
     * ★ 메시지는 생성과 수정이 다릅니다.
     *   shape 는 **백지 생성**에서 시키는 말입니다("<ul> 안에 <li> 로").
     *   템플릿 블록을 수정하다 걸렸을 때 그 문장을 주면
     *   모델이 .benefit-card 구조를 <ul><li> 로 갈아엎습니다. 디자인이 망가집니다.
     */
    private static void checkShape(Block b, Element el, List<Failure> f, boolean editing) {
        if (b.must() == null) return;

        if (el.selectFirst(b.must()) == null) {
            f.add(new Failure("empty_" + b.key(), editing
                    ? b.key() + " 안에 내용이 없습니다. 원래 구조를 그대로 두고 문구만 고치세요."
                    : b.key() + " 안에 " + b.shape() + " — 맨 텍스트만 두면 안 됩니다."));
            return;
        }
        if (b.minItems() > 0) {
            // ★ li 를 하드코딩하지 않는다. must() 가 곧 세는 기준이다.
            //   must 가 "ul li, .benefit-card" 라 백지도 템플릿도 같은 코드로 센다.
            int n = el.select(b.must()).size();
            if (n < b.minItems()) {
                f.add(new Failure("few_" + b.key(),
                        b.key() + " 항목이 " + n + "개입니다. " + b.minItems() + "개 이상 필요합니다."));
            }
        }
    }

    // ── 보존 검사 ──────────────────────────────────────────────────

    /**
     * 건드리면 안 되는 "이름표" 가 그대로인가. **개수가 아니라 집합을 본다.**
     *
     * ★ 왜 개수가 아닌가
     *   템플릿 스크립트는 5종 전부 이벤트 위임이다 —
     *   document 에 리스너 하나 걸고 closest('.cta-btn, .btn') 으로 판정한다.
     *   그래서 **버튼을 복제해도 그대로 동작한다.**
     *   개수를 묶으면 "복주머니를 2개만 보여줘"(= 카드 삭제)가 영원히 실패한다.
     *   계약 3표의 "혜택/단계 카드 추가·삭제" 도 막힌다.
     *
     * ★ 대신 이 둘은 고정이다
     *   data-slot  서버가 값을 쓰는 자리. 없어지면 그 이벤트는 영영 못 채운다
     *   id         getElementById 로 직접 찾는다 (#demoTimer · #regCount · #pouchSection)
     *              없어지면 타이머가 멈추고, 복제하면 id 중복으로 첫 번째만 잡힌다
     */
    private static void checkPreserved(String before, String after, List<Failure> f) {
        diff(Slots.keysOf(before), Slots.keysOf(after), f,
                "slot_lost_", "data-slot=\"%s\" 가 붙은 태그를 지웠습니다. "
                        + "그 자리는 서버가 채우는 곳입니다. 태그와 속성을 원래대로 두세요.",
                "slot_invented_", "data-slot=\"%s\" 를 새로 만들었습니다. "
                        + "data-slot 은 서버만 심습니다. 원래 있던 것만 그대로 두세요.");

        diff(Slots.idsOf(before), Slots.idsOf(after), f,
                "id_lost_", "id=\"%s\" 를 지웠습니다. 화면 기능이 그 id 로 요소를 찾습니다. "
                        + "원래 있던 id 를 그대로 두세요.",
                "id_invented_", "id=\"%s\" 를 새로 만들었습니다. "
                        + "id 는 화면 기능이 쓰는 이름이라 임의로 추가하면 안 됩니다.");
    }

    private static void diff(Set<String> was, Set<String> now, List<Failure> f,
                             String lostCode, String lostMsg,
                             String newCode, String newMsg) {
        for (String k : was) {
            if (!now.contains(k)) f.add(new Failure(lostCode + k, String.format(lostMsg, k)));
        }
        for (String k : now) {
            if (!was.contains(k)) f.add(new Failure(newCode + k, String.format(newMsg, k)));
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
     * 생성 결과 정화 — 모델이 만든 문서에 건다.
     *
     * 지우는 것: data-slot · button · input · id · 인터랙션 data-* 속성
     *
     * ★ 왜 지우나
     *   백지 생성 결과에는 그걸 물고 동작할 템플릿 스크립트가 없다.
     *   모델이 버튼을 만들어도 눌리지 않는 껍데기가 생길 뿐이고,
     *   data-slot 을 만들면 서버의 쓰기 지점을 모델이 만드는 셈이 된다 (REQ-LLM-46).
     */
    public static String sanitizeGenerated(String html) {
        return clean(html, false);
    }

    /**
     * 수정 결과 정화 — 템플릿에서 온 블록에 건다.
     *
     * 남기는 것: data-slot · button · input · id · 인터랙션 data-* 속성
     *
     * ★ 생성과 반대다. 원본에 이미 있던 것을 보존해야 하기 때문이다.
     *   계약 EVENT_STRUCTURE_CONTRACT §4-4 가 명시적으로 요구한다 —
     *   "data-demo-msg, data-state 를 반드시 보존", "#demoTimer, #regCount, #pouchSection 유지".
     *
     *   대신 모델이 지어낸 것은 validateEdited 의 checkPreserved 가 잡는다.
     *   **"보존" 과 "대조" 는 한 쌍이다.** 하나만 있으면 뚫린다.
     *
     * ★ on* 핸들러는 걱정하지 않아도 된다
     *   Safelist 는 명시 허용만 하므로 onclick 은 애초에 못 들어온다.
     *   계약 4-⑤ 도 "CTA 버튼에 onclick 인라인 핸들러를 넣지 않습니다" 로 같은 방향이다.
     */
    public static String sanitizeEdited(String html) {
        return clean(html, true);
    }

    /**
     * ★ 이 함수는 "모델이 준 조각" 에만 건다.
     *   서버가 조립한 최종 문서에 걸면 문서 밖 script 가 다 죽는다.
     *   순서:  sanitize(모델 출력) → merge   (반대로 하지 말 것)
     */
    private static String clean(String html, boolean keepInteractive) {
        Safelist s = Safelist.relaxed()
                .addAttributes(":all", "style", "data-block", "class")
                .addTags("section")
                // ★ href="#" 를 살리는 줄. 빼면 CTA 버튼의 링크가 통째로 사라진다.
                //   Safelist.relaxed() 는 a[href] 에 ftp/http/https/mailto 만 허용하고,
                //   상대 URL 은 절대 URL 로 바꾼 뒤 검사한다. baseUri 가 비어 있으면
                //   "#" 은 빈 문자열이 되어 어느 프로토콜에도 안 맞고 속성째 제거된다.
                .addProtocols("a", "href", "#");

        if (keepInteractive) {
            s = s.addTags("button", "input")
                 .addAttributes(":all",
                         "data-slot",      // 서버가 값을 쓰는 자리
                         "id",             // getElementById 로 찾는 것들
                         "type",           // <button type="button">
                         "value", "placeholder",
                         "data-href",      // <button> 일 때 서버가 넣는 링크
                         "data-demo-msg", "data-state", "data-vote");
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
