package com.newvent.registry;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 슬롯을 읽고 · 비우고 · 채운다.
 *
 * ★ 무엇을 어떻게 다룰지는 여기가 아니라 Slot.Kind 가 안다.
 *   여기에 if (period) ... else if (cta-link) 를 쓰지 말 것.
 *   슬롯이 늘 때마다 이 파일을 고쳐야 하면 레지스트리를 만든 의미가 없다.
 *
 *     TEXT  내용형 — 태그 안의 글자를 쓴다
 *     LINK  링크형 — href / data-href 를 쓴다. **글자는 안 건드린다**
 *
 *   이걸 구분 안 하면 <button data-slot="cta-link">쿠폰 받기</button> 를 비울 때
 *   버튼 문구가 같이 날아간다.
 */
public final class Slots {

    private Slots() {}

    // ── 읽기 ──────────────────────────────────────────────────────

    /** 이 HTML 안에 있는 data-slot 키들 */
    public static Set<String> keysOf(String html) {
        Set<String> keys = new LinkedHashSet<>();
        for (Element el : parse(html).body().select("[data-slot]")) {
            String k = el.attr("data-slot").trim();
            if (!k.isEmpty()) keys.add(k);
        }
        return keys;
    }

    /** 이 HTML 안에 있는 id 들 — 템플릿 스크립트가 getElementById 로 찾는 것들 */
    public static Set<String> idsOf(String html) {
        Set<String> ids = new LinkedHashSet<>();
        for (Element el : parse(html).body().select("[id]")) {
            String v = el.id().trim();
            if (!v.isEmpty()) ids.add(v);
        }
        return ids;
    }

    // ── 비우기 ────────────────────────────────────────────────────

    /**
     * 슬롯을 빈 상태로 만든다. 템플릿을 이벤트의 초기 버전으로 저장하기 전에 건다.
     *
     * ★ 왜 비우나
     *   템플릿에는 데모 값이 들어 있다(2026.09.10 ~ 2026.09.30).
     *   그대로 저장하면 그 날짜가 굳어서, 폼에서 기간만 고쳐도 화면이 안 바뀐다.
     *   REQ-LLM-13 "폼 값은 자동 보존, HTML 이 바뀔 때만 버전 생성" 이 깨진다.
     *
     *   관리자가 자기 이벤트를 템플릿으로 저장할 때도 같다.
     *   안 비우면 남의 이벤트 기간과 참여 링크가 라이브러리에 올라간다.
     *
     * ★ 태그는 남긴다. 비우는 것과 지우는 것은 다르다.
     */
    public static String clear(String html) {
        Document doc = parse(html);
        for (Element el : doc.body().select("[data-slot]")) {
            Slot.find(el.attr("data-slot").trim()).ifPresent(s -> {
                switch (s.kind()) {
                    case TEXT -> el.text("");
                    case LINK -> {
                        el.removeAttr("href");
                        el.removeAttr("data-href");
                    }
                }
            });
            // 모르는 키는 손대지 않는다. checkPreserved 가 slot_invented 로 잡는다.
        }
        return doc.body().html();
    }

    // ── 채우기 ────────────────────────────────────────────────────

    /**
     * 서버가 값을 넣는다. 저장할 때가 아니라 **보여줄 때** 부른다.
     *
     * ★ 저장 경로에서 부르면 값이 박혀서 들어가고, clear() 를 한 이유가 없어진다.
     *   순서:  DB 에서 꺼낸 HTML(슬롯 빈 상태) → fill() → 미리보기 / 게시 응답
     */
    public static String fill(String html, Map<Slot, String> values) {
        Document doc = parse(html);
        for (Map.Entry<Slot, String> e : values.entrySet()) {
            String value = e.getValue();
            if (value == null || value.isBlank()) continue;

            for (Element el : doc.body().select(e.getKey().selector())) {
                write(el, e.getKey().kind(), value);
            }
        }
        return doc.body().html();
    }

    /** 지금 쓰는 슬롯 둘을 한 번에 — null 인 값은 건너뛴다 */
    public static String fill(String html, String period, String ctaUrl) {
        Map<Slot, String> v = new LinkedHashMap<>();
        if (period != null) v.put(Slot.PERIOD, period);
        if (ctaUrl != null) v.put(Slot.CTA_LINK, ctaUrl);
        return fill(html, v);
    }

    private static void write(Element el, Slot.Kind kind, String value) {
        switch (kind) {
            case TEXT -> el.text(value);
            case LINK -> {
                // <a> 면 href, 그 외(템플릿 5종은 전부 <button>)면 data-href.
                // 호스트 스크립트가 이벤트 위임으로 읽어간다.
                if ("a".equals(el.tagName())) el.attr("href", value);
                else                          el.attr("data-href", value);
            }
        }
    }

    private static Document parse(String html) {
        Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);
        doc.outputSettings().prettyPrint(false);
        return doc;
    }
}
