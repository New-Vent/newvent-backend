package com.newvent.registry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 페이지 색감 — 모델이 **고르기만** 하는 팔레트.
 *
 * ★ 어떻게 적용되나
 *   모델은 페이지 루트를 못 만진다(블록 단위로만 출력한다). 그래서
 *   hero 섹션의 class 에 palette-* 를 붙이게 하고, 저장 직전에 서버가
 *   그걸 루트(.ev-container)로 옮긴다 — {@link PageShell#hoistPalette}.
 *   hero 를 고르는 이유: 모든 페이지에 있고(필수), 페이지 인상을 정하는 자리라서다.
 *
 * ★ 팔레트는 CSS 변수(--ev-primary 등)만 바꾼다
 *   event.css 의 `.palette-*` 규칙. 테마(theme-*)와 같은 변수를 덮어쓰고,
 *   파일에서 테마보다 뒤에 있어서 둘 다 있으면 팔레트가 이긴다.
 *   템플릿 전용 규칙 중 색을 변수 없이 적어 둔 곳(.theme-holiday .hl-hero 등)은 안 바뀐다.
 *
 * ★ BASE 는 "원래대로" 다. 루트의 팔레트를 지우고 아무것도 붙이지 않는다.
 */
public enum Palette {

    BASE("palette-base", "원래 색으로 되돌린다 (템플릿 테마 또는 기본 남색)"),
    SPRING("palette-spring", "봄 — 벚꽃 분홍, 연두"),
    SUMMER("palette-summer", "여름 — 바다 파랑, 청량한 하늘색"),
    AUTUMN("palette-autumn", "가을 — 단풍 주황, 갈색"),
    WINTER("palette-winter", "겨울 — 눈 내린 남색, 은빛"),
    FOREST("palette-forest", "숲 · 친환경 — 초록"),
    SUNSET("palette-sunset", "노을 — 코랄, 보라"),
    NIGHT("palette-night", "밤 · 프리미엄 — 검정, 금색"),
    PASTEL("palette-pastel", "파스텔 — 연보라, 민트"),
    MONO("palette-mono", "모노 · 미니멀 — 검정, 회색"),
    FESTIVE("palette-festive", "축제 · 명절 — 빨강, 금색"),

    // ★ 아래 8개는 event.css 의 90-palettes/02-extra-set.css 와 짝이다.
    //   프런트가 먼저 넣었고 여기에 이름이 없어서 **쓰이지 못하고 있었다** —
    //   레지스트리에 없는 이름은 프롬프트 목록에 안 들어가고(PromptBuilder),
    //   모델이 어쩌다 써도 허용 목록 밖이라 지워진다(BlockValidator.cleanLooks).
    //   "크리스마스 분위기로" 가 안 먹던 이유가 이것이다.
    CHRISTMAS("palette-christmas", "크리스마스 — 전나무 초록, 선물 빨강"),
    NEWYEAR("palette-newyear", "새해 — 남색, 금색"),
    VALENTINE("palette-valentine", "발렌타인 · 화이트데이 — 분홍, 자주"),
    KIDS("palette-kids", "어린이 · 가족 — 밝은 파랑, 노랑"),
    ESPORTS("palette-esports", "게임 · e스포츠 — 네온 초록, 검정"),
    LAVENDER("palette-lavender", "라벤더 — 연보라, 차분한 분위기"),
    COFFEE("palette-coffee", "카페 · 베이커리 — 갈색, 크림"),
    BRAND("palette-brand", "브랜드 기본 — 핑크");

    public static final String PREFIX = "palette-";

    private final String cssClass;
    private final String desc;

    Palette(String cssClass, String desc) {
        this.cssClass = cssClass;
        this.desc = desc;
    }

    public String cssClass() { return cssClass; }
    public String desc()     { return desc; }

    public static Optional<Palette> find(String cssClass) {
        if (cssClass == null) return Optional.empty();
        return Arrays.stream(values()).filter(p -> p.cssClass.equals(cssClass)).findFirst();
    }

    public static boolean looksLike(String cssClass) {
        return cssClass != null && cssClass.startsWith(PREFIX);
    }

    /** 프롬프트에 나갈 목록 */
    public static List<Palette> all() {
        return List.of(values());
    }
}
