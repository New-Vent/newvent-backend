package com.newvent.registry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jsoup.nodes.Element;

/**
 * 문구 안쪽 꾸밈 — 배지 · 형광펜 · 강조색. 모델이 **고르기만** 하는 class 목록.
 *
 * ★ Variant 와의 차이
 *   Variant  블록 루트 &lt;section&gt; 에 붙는다. 블록 전체의 배치·배경
 *   Inline   문구 속 &lt;span&gt; · &lt;mark&gt; · &lt;strong&gt; 에 붙는다. 단어 하나를 꾸민다
 *     예) &lt;span class="t-badge t-badge-red"&gt;NEW&lt;/span&gt; 데이터 3GB 즉시 지급
 *
 * ★ 왜 Tailwind 류(Preline · HyperUI)를 그대로 쓰지 않나
 *   이벤트 페이지는 Tailwind 를 싣지 않는다. 그리고 유틸리티 class 를 모델에게 열면
 *   조합이 무한해서 검증할 수 없다. 그 디자인을 이름 붙은 class 몇 개로 옮겨 온 게 이것이다.
 *
 * ★ 템플릿 블록에도 걸린다 — event.css 의 .t-* 는 :not(.ev-block) 으로 막지 않는다.
 *   문구 속 작은 요소라 템플릿 디자인과 부딪치지 않는다.
 *
 * ★ 이 파일과 event.css 의 .t-* 는 짝이다.
 */
public enum Inline {

    BADGE("t-badge", Kind.BASE, "작은 둥근 배지 (브랜드 연한색) — 다른 색을 쓸 때도 t-badge 를 같이 붙인다"),
    BADGE_SOLID("t-badge-solid", Kind.COLOR, "진한 브랜드색 배지"),
    BADGE_DARK("t-badge-dark", Kind.COLOR, "검정 배지"),
    BADGE_RED("t-badge-red", Kind.COLOR, "빨간 배지 — 마감 · 한정"),
    BADGE_YELLOW("t-badge-yellow", Kind.COLOR, "노란 배지 — 주의 · 인기"),
    BADGE_GREEN("t-badge-green", Kind.COLOR, "초록 배지 — 혜택 · 무료"),
    BADGE_TEAL("t-badge-teal", Kind.COLOR, "청록 배지 — 신규"),
    BADGE_OUTLINE("t-badge-outline", Kind.COLOR, "테두리만 있는 배지"),

    HIGHLIGHT("t-highlight", Kind.TEXT, "형광펜 밑줄 — 핵심 단어"),
    ACCENT("t-accent", Kind.TEXT, "브랜드색 굵은 글씨 — 숫자 · 혜택명");

    /** 배지 바탕(BASE)은 색(COLOR)과 함께 쓴다. TEXT 는 따로 쓴다 */
    public enum Kind { BASE, COLOR, TEXT }

    public static final String PREFIX = "t-";

    /**
     * 배지에 담을 수 있는 글자 수(공백 제외). 넘으면 배지 class 를 뗀다 — 글자는 남긴다.
     *
     * ★ 왜 서버가 자르나 — 프롬프트로 "짧게" 라고 해도 문장 전체에 배지를 달았다
     *   ("본 이벤트는 1인 1회 참여…" 에 t-badge-red, Bedrock 실측). 배지는 nowrap 이라
     *   긴 글은 줄바꿈 없이 화면 밖으로 밀린다. 디자인이 깨지는 건 막는 쪽이 맞다.
     *   10 은 "편의점 5,000원"(공백 빼고 10자) 이 들어가는 정도다.
     */
    public static final int BADGE_MAX = 10;

    /** 붙일 수 있는 태그. 블록 · 목록 같은 덩어리에는 안 붙인다 */
    private static final List<String> TAGS = List.of("span", "mark", "strong", "em");

    private final String cssClass;
    private final Kind kind;
    private final String desc;

    Inline(String cssClass, Kind kind, String desc) {
        this.cssClass = cssClass;
        this.kind = kind;
        this.desc = desc;
    }

    public String cssClass() { return cssClass; }
    public Kind kind()       { return kind; }
    public String desc()     { return desc; }

    public static Optional<Inline> find(String cssClass) {
        if (cssClass == null) return Optional.empty();
        return Arrays.stream(values()).filter(i -> i.cssClass.equals(cssClass)).findFirst();
    }

    public static boolean looksLike(String cssClass) {
        return cssClass != null && cssClass.startsWith(PREFIX);
    }

    /**
     * 요소 하나의 t-* class 를 정리한다. **모델 출력에만 건다.**
     *
     * 지우는 것
     *   - 목록에 없는 t-*
     *   - 허용 태그(span · mark · strong · em)가 아닌 요소의 t-*
     *   - 배지 색이 둘 이상이면 두 번째부터
     *
     * 채우는 것
     *   - 배지 색만 있고 t-badge 가 없으면 붙인다 (모델이 자주 빠뜨릴 자리)
     *
     * 떼는 것
     *   - 글이 {@link #BADGE_MAX} 자를 넘는 배지 — 배지 class 만 떼고 글은 둔다
     */
    public static void sanitize(Element el) {
        boolean allowedTag = TAGS.contains(el.normalName());
        boolean colorSeen = false;
        for (String c : List.copyOf(el.classNames())) {
            if (!looksLike(c)) continue;
            Optional<Inline> i = find(c);
            boolean keep = allowedTag && i.isPresent();
            if (keep && i.get().kind == Kind.COLOR) {
                keep = !colorSeen;
                colorSeen = true;
            }
            if (!keep) el.removeClass(c);
        }
        if (colorSeen && !el.hasClass(BADGE.cssClass)) el.addClass(BADGE.cssClass);

        if (el.hasClass(BADGE.cssClass) && el.text().replaceAll("\\s+", "").length() > BADGE_MAX) {
            for (String c : List.copyOf(el.classNames())) {
                Inline.find(c).filter(i -> i.kind != Kind.TEXT).ifPresent(i -> el.removeClass(c));
            }
        }
    }
}
