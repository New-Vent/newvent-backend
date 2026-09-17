package com.newvent.registry;

import java.util.ArrayList;
import java.util.List;

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
            if (b.must() != null && el.selectFirst(b.must()) == null) {
                f.add(new Failure("empty_" + b.key(),
                        b.key() + " 안에 " + b.shape() + " — 맨 텍스트만 두면 안 됩니다."));
            } else if (b.minItems() > 0) {
                int n = el.select("li").size();
                if (n < b.minItems()) {
                    f.add(new Failure("few_" + b.key(),
                            b.key() + " 항목이 " + n + "개입니다. " + b.minItems() + "개 이상 필요합니다."));
                }
            }
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

    /** 수정 결과 검증 — 그 블록만 왔는가 */
    public static List<Failure> validateEdited(Block target, String html) {
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
        if (target.must() != null && el.selectFirst(target.must()) == null) {
            f.add(new Failure("empty_" + target.key(),
                    target.key() + " 안에 " + target.shape()));
        }
        return f;
    }

    /**
     * 정화 — 명세 24행. <script> 제거, style 속성만 허용.
     * 모델이 JS 를 잘 쓰더라도(qwen2.5 30/30) 검토 안 된 코드는 실행시키지 않습니다.
     */
    public static String sanitize(String html) {
        Safelist s = Safelist.relaxed()
                .addAttributes(":all", "style", "data-block", "class")
                .addTags("section");
        return Jsoup.clean(html, "", s, new Document.OutputSettings().prettyPrint(false));
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
}
