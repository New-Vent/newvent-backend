package com.newvent.registry;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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

    /**
     * **이름표 붙은 요소**의 class 를 뽑는다. 수정 전후를 대조하는 데 쓴다.
     *
     * ★ 왜 전체 class 를 대조하지 않는가
     *   계약이 허용하는 수정이 class 집합을 **정당하게** 바꾼다.
     *     · 복주머니 카드 하나 삭제 → hl-pouch-blue 가 사라진다
     *     · "이 문구 강조해줘"      → span.highlight 가 늘어난다
     *   전체를 묶으면 이 둘이 영원히 실패한다.
     *
     * ★ 그래서 세 군데만 고정한다
     *     root        블록 루트 <section> — ev-block 이 여기 붙어 있다
     *     slot:키     서버가 값을 쓰는 자리
     *     id:이름     스크립트가 getElementById 로 잡는 요소
     *   전부 "지워지면 조용히 망가지는데 검증기가 달리 알 길이 없는" 것들이다.
     *
     * ★ ev-block 이 제일 위험하다
     *   event.css 가 ul/li 기본 스타일을 :not(.ev-block) 으로 건다.
     *   백지 생성 결과에 폴백 스타일을 주려고 만든 장치인데,
     *   템플릿에서 ev-block 이 떨어지면 그 폴백이 템플릿에 **걸린다.**
     *   태그도 슬롯도 id 도 멀쩡한데 디자인만 달라져서 아무 검사에도 안 걸린다.
     *
     * @return 키는 root / slot:period / id:demoTimer 형태, 값은 정규화한 class 문자열
     */
    public static Map<String, String> classMap(String html) {
        Map<String, String> out = new LinkedHashMap<>();
        Document doc = parse(html);

        Element root = doc.body().selectFirst("section[data-block]");
        if (root != null) out.put("root", normalize(root.className()));

        for (Element el : doc.body().select("[data-slot]")) {
            String k = el.attr("data-slot").trim();
            if (!k.isEmpty()) out.put("slot:" + k, normalize(el.className()));
        }
        for (Element el : doc.body().select("[id]")) {
            String v = el.id().trim();
            if (!v.isEmpty()) out.put("id:" + v, normalize(el.className()));
        }
        return out;
    }

    /**
     * 순서·중복·공백 차이는 무시한다. class 는 순서에 의미가 없다.
     *
     * 이게 없으면 모델이 "ev-block block-hero" 를 "block-hero ev-block" 으로
     * 돌려주기만 해도 실패로 잡히고, 재시도 4번이 전부 같은 이유로 터진다.
     */
    private static String normalize(String className) {
        return new TreeSet<>(List.of(className.trim().split("\\s+"))).stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
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
