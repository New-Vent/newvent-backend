package com.newvent.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class Looks {

    private Looks() {}

    /** 고른 것을 붙일 수 없다. 문구는 관리자에게 그대로 보인다 */
    public static class Blocked extends RuntimeException {
        public Blocked(String reason) {
            super(reason);
        }
    }

    // ── 붙이기 ────────────────────────────────────────────────────

    /**
     * 고른 것을 붙이고 저장할 조각을 돌려준다.
     *
     * @param layout   페이지 배치. 안 고쳤으면 null
     * @param variants 영역 → 모양. 값이 null 이면 모양 class 를 뗀다 (아무것도 안 고른 처음 모양)
     */
    public static String apply(String fragment, Layout layout, Map<Block, Variant> variants) {
        Document doc = parse(fragment);
        Element root = PageShell.rootOf(doc.body());
        if (root == null) throw new Blocked("페이지가 아직 없습니다.");
        if (isTemplate(root)) {
            throw new Blocked("템플릿으로 만든 페이지는 배치 · 모양을 바꿀 수 없습니다. 문구와 순서는 바꿀 수 있습니다.");
        }

        for (Map.Entry<Block, Variant> e : variants.entrySet()) {
            setVariant(section(root, e.getKey()), e.getKey(), e.getValue());
        }
        if (layout != null) setLayout(doc, root, layout);

        // 순서 · 조건을 저장 직전과 똑같이 한 번 더 본다
        return PageShell.settle(doc.body().html());
    }

    private static void setVariant(Element sec, Block b, Variant v) {
        List<Variant> own = Variant.layoutsOf(b);
        if (v != null) {
            if (!own.contains(v)) throw new Blocked("이 영역에는 쓸 수 없는 모양입니다.");
            String why = variantBlocked(sec, b, v);
            if (why != null) throw new Blocked(why);
        }
        for (Variant o : own) sec.removeClass(o.cssClass());
        // ★ 첫 번째 모양도 class 를 붙인다 — hero · steps · cta 는 첫 모양에 자기 규칙이 있어서
        //   class 가 없는 것과 다르게 그려진다 (v-hero-center · v-steps-numbered · v-cta-center)
        if (v != null) sec.addClass(v.cssClass());
        if (sec.classNames().isEmpty()) sec.removeAttr("class");
    }

    private static void setLayout(Document doc, Element root, Layout want) {
        Layout current = root.classNames().stream().filter(Layout::looksLike)
                .map(c -> Layout.find(c).orElse(null)).filter(java.util.Objects::nonNull)
                .findFirst().orElse(Layout.DEFAULT);
        if (want == current) return;
        String why = PageShell.layoutBlocked(doc.body().html(), want);
        if (why != null) throw new Blocked(why);
        for (String c : List.copyOf(root.classNames())) {
            if (Layout.looksLike(c)) root.removeClass(c);
        }
        if (want != Layout.DEFAULT) root.addClass(want.cssClass());
    }

    // ── 선택지 ────────────────────────────────────────────────────

    /** 배치 하나. blocked 가 있으면 지금은 못 고른다 (그 이유) */
    public record LayoutOption(String cssClass, String label, String desc, String blocked) {}

    /** 영역 모양 하나 */
    public record VariantOption(String cssClass, String desc, String blocked) {}

    /**
     * 영역 하나의 지금 상태와 선택지.
     *
     * @param variant     지금 모양. 모양 class 가 없으면 빈 문자열
     */
    public record BlockOption(String variant, List<VariantOption> variants) {}

    /**
     * @param template 템플릿 페이지인가. 그러면 아무것도 못 고른다
     * @param layout   지금 배치 (class 가 없으면 layout-stack)
     * @param blocks   페이지에 있는 영역 — 보이는 순서. 유의사항은 뺀다 (서버 소유)
     */
    public record Catalog(boolean template, String layout, List<LayoutOption> layouts,
                          Map<String, BlockOption> blocks) {}

    /** 지금 페이지에서 고를 수 있는 것 — 편집 화면이 그대로 그린다 */
    public static Catalog catalog(String fragment) {
        Document doc = parse(fragment);
        Element root = PageShell.rootOf(doc.body());
        if (root == null) return null;
        boolean template = isTemplate(root);
        String html = doc.body().html();

        Layout current = root.classNames().stream().filter(Layout::looksLike)
                .map(c -> Layout.find(c).orElse(null)).filter(java.util.Objects::nonNull)
                .findFirst().orElse(Layout.DEFAULT);
        List<LayoutOption> layouts = new ArrayList<>();
        for (Layout l : Layout.all()) {
            String why = (l == current) ? null : PageShell.layoutBlocked(html, l);
            layouts.add(new LayoutOption(l.cssClass(), l.label(), l.desc(), why));
        }

        Map<String, BlockOption> blocks = new LinkedHashMap<>();
        for (Element sec : sections(root)) {
            Block b = Block.find(sec.attr("data-block")).orElse(null);
            if (b == null || b == Block.NOTICES || blocks.containsKey(b.key())) continue;
            List<Variant> own = Variant.layoutsOf(b);
            List<VariantOption> options = new ArrayList<>();
            String now = "";
            for (Variant v : own) {
                if (sec.hasClass(v.cssClass())) now = v.cssClass();
                options.add(new VariantOption(v.cssClass(), v.desc(), template ? null : variantBlocked(sec, b, v)));
            }
            blocks.put(b.key(), new BlockOption(now, options));
        }
        return new Catalog(template, current.cssClass(), layouts, blocks);
    }

    // ── 도구 ──────────────────────────────────────────────────────

    /** 항목이 모자라 못 쓰는 모양이면 그 이유. 쓸 수 있으면 null */
    private static String variantBlocked(Element sec, Block b, Variant v) {
        int need = v.needsItems();
        if (need == 0 || b.must() == null) return null;
        int items = sec.select(b.must()).size();
        if (items >= need) return null;
        return "이 모양은 항목이 " + need + "개 이상일 때 쓸 수 있습니다. 지금은 " + items + "개입니다.";
    }

    private static Element section(Element root, Block b) {
        Element sec = root.selectFirst("> " + b.selector());
        if (sec == null) throw new Blocked("고른 영역(" + b.key() + ")이 페이지에 없습니다.");
        return sec;
    }

    private static List<Element> sections(Element root) {
        return root.select("> section[data-block]");
    }

    /**
     * 템플릿으로 만든 페이지인가.
     * ★ 유의사항은 보지 않는다 — 서버가 넣는 승인 문구가 .ev-block 을 달고 있어서,
     *   세면 모든 페이지가 템플릿 페이지가 된다 (PageShell.settleLayout 과 같은 기준)
     */
    private static boolean isTemplate(Element root) {
        return sections(root).stream()
                .anyMatch(s -> s.hasClass("ev-block") && !Block.NOTICES.key().equals(s.attr("data-block")));
    }

    private static Document parse(String html) {
        Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);
        // PageShell.parse 와 같다 — 조각의 공백을 그대로 둔다
        doc.outputSettings().prettyPrint(false);
        return doc;
    }
}
