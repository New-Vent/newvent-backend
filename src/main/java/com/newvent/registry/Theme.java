package com.newvent.registry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 페이지 분위기 — 템플릿이 쓰는 테마와, 백지에서 모델이 고르는 테마.
 *
 * ★ 왜 레지스트리로 옮기는가
 *   같은 매핑이 {@code TemplateResponse.themeOf()} 에 이미 있었다.
 *   거기는 화면용 DTO 라서 HTML 을 만드는 쪽에서는 쓸 수 없었고,
 *   그래서 저장되는 조각에는 테마가 한 번도 들어가지 않았다.
 *   매핑이 두 벌이 되면 언젠가 갈라진다. 한 벌로 둔다.
 *
 * ★ 테마는 <b>구조</b>, 팔레트는 <b>색</b>이다
 *   테마가 배경 · 컨테이너 카드 · 타이포 단계 · 섹션 리듬을 정하고,
 *   팔레트({@link Palette})가 CSS 변수로 색을 덮는다. 층이 겹치지 않으므로
 *   백지 테마 5 × 팔레트 144 가 그대로 조합이 된다. 계절 · 명절은 팔레트의 몫이다 —
 *   테마를 계절별로 늘리면 팔레트와 역할이 겹쳐 둘 다 망가진다.
 *
 * ★ suffix 가 null 이면 백지 테마다
 *   템플릿 코드로는 고를 수 없고, 모델이 hero 의 class 에 붙여 고른다.
 *   저장 직전 서버가 루트로 옮긴다 — {@link PageShell#hoistPalette} 와 같은 길.
 *
 * ★ 기본값({@link #DEFAULT})은 성격이 없어야 한다
 *   모델이 안 고르거나 지어냈을 때 박히는 값이다. BLOOM 처럼 센 테마를 기본으로 두면
 *   "법인 요금제 비교" 에 구름이 뜬다. 조용한 기본값은 조금 안 맞아도 괜찮지만
 *   시끄러운 기본값은 그냥 틀린다. 그래서 BASIC 이 기본이다.
 */
public enum Theme {

    SPORTS("sports_cheer",          "theme-sports",  Tone.LIGHT, null),
    HOLIDAY("holiday_gift",         "theme-holiday", Tone.LIGHT, null),
    VIP("member_appreciation",      "theme-vip",     Tone.DARK,  null),
    SALE("flash_sale",              "theme-sale",    Tone.LIGHT, null),
    LAUNCH("pre_registration",      "theme-launch",  Tone.DARK,  null),
    BASIC(null, "theme-basic", Tone.LIGHT,
            "차분 · 일반 — 분위기가 분명하지 않거나 업무적인 이벤트. 장식 없이 읽기 좋게"),
    BLOOM(null, "theme-bloom", Tone.LIGHT,
            "발랄 · 축제 · 어린이 · 먹거리 — 밝은 배경에 구름, 통통한 외곽선 제목"),
    AURORA(null, "theme-aurora", Tone.DARK,
            "밤 · 프리미엄 · 게임 · 신비 — 어두운 배경에 별가루, 떠 있는 한 장의 카드"),
    // ★★ 상자를 안 쓰는 벌. 다른 셋과 **같은 HTML** 로 돌아간다 —
    //   영문 소제목(sub-label)도 배경 변형(v-surface-*)도 생성 쪽은 그대로 두고
    //   CSS 가 숨기거나 걷는다(20-themes/05-editorial.css).
    //   그래서 테마를 되돌리면 원래 모양이 그대로 돌아온다.
    EDITORIAL(null, "theme-editorial", Tone.LIGHT,
            "여백 · 잡지 · 브랜드 지면 — 상자 없이 큰 제목과 여백으로 정리. 깔끔하게 · 정돈되게"),
    BRUTALIST(null, "theme-brutalist", Tone.LIGHT,
            "브루탈 · 개성 · 스트리트 · 힙 — 두꺼운 검정 테두리, 어긋난 그림자, 또렷한 원색 면"),
    PAPERCRAFT(null, "theme-papercraft", Tone.LIGHT,
            "종이 공예 · 아기자기 · 수공예 · 따뜻한 — 종이를 오려 겹친 카드, 부드러운 층 그림자");

    /**
     * 표면 밝기.
     *
     * ★ 지금은 아무 곳에서도 분기에 쓰지 않는다. 다음 테마를 추가할 때
     *   "어두운 테마가 표면색을 팔레트에 맡겼다" 를 테스트로 잡기 위한 선언이다.
     *   어두운 테마는 표면색을 var(--ev-surface) 로 받지 않는다 — 팔레트 파일(90-)이
     *   테마 파일(20-)보다 뒤라 --ev-surface 는 팔레트가 이기는데 --ev-text-main 은
     *   팔레트가 거의 안 건드린다(night · esports 뿐). 그러면 카드만 밝아지고
     *   글자는 밝은 채로 남아 사라진다. 실제로 렌더해서 봤다.
     */
    public enum Tone { LIGHT, DARK }

    /** 테마 클래스의 공통 접두사. 이미 테마가 붙었는지 판정할 때 쓴다. */
    public static final String PREFIX = "theme-";

    /** 모델이 안 고르거나 지어냈을 때 박는 테마 */
    public static final Theme DEFAULT = BASIC;

    private final String suffix;
    private final String cssClass;
    private final Tone tone;
    private final String desc;

    Theme(String suffix, String cssClass, Tone tone, String desc) {
        this.suffix = suffix;
        this.cssClass = cssClass;
        this.tone = tone;
        this.desc = desc;
    }

    public String cssClass() { return cssClass; }
    public Tone tone()       { return tone; }
    /** 프롬프트에 나갈 설명. 템플릿 전용 테마는 null */
    public String desc()     { return desc; }

    /**
     * 템플릿 코드로 테마를 고른다. 모르는 코드면 빈 값.
     *
     * ★ suffix 가 null 인 백지 테마는 건너뛴다 — endsWith(null) 은 NPE 다.
     */
    public static Optional<Theme> of(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) return Optional.empty();
        String k = templateCode.trim();
        return Arrays.stream(values())
                .filter(t -> t.suffix != null && k.endsWith(t.suffix))
                .findFirst();
    }

    /** 화면용 — 모르는 코드면 null. 기존 DTO 의 계약을 그대로 유지한다. */
    public static String cssClassOf(String templateCode) {
        return of(templateCode).map(Theme::cssClass).orElse(null);
    }

    /**
     * class 이름으로 찾는다. 모델이 지어낸 이름(theme-두쫀쿠)이면 빈 값.
     *
     * ★ 이게 있어야 {@code BlockValidator.cleanLooks} 가 지어낸 테마를 지울 수 있다.
     */
    public static Optional<Theme> find(String cssClass) {
        if (cssClass == null) return Optional.empty();
        return Arrays.stream(values()).filter(t -> t.cssClass.equals(cssClass)).findFirst();
    }

    public static boolean looksLike(String cssClass) {
        return cssClass != null && cssClass.startsWith(PREFIX);
    }

    /**
     * 백지에서 모델에게 보여줄 테마 — 템플릿 전용은 뺀다.
     *
     * ★ 템플릿 테마를 보여주면 안 된다. .theme-sports 의 규칙은 템플릿 마크업(.sp-*)을
     *   전제하므로 백지 마크업에는 거의 안 걸린다. 골라도 화면이 안 바뀐다.
     */
    public static List<Theme> blankThemes() {
        return Arrays.stream(values()).filter(t -> t.suffix == null).toList();
    }

    /**
     * 요청문과 이름(desc 의 — 앞)이 가장 많이 맞는 백지 테마 하나. 없으면 빈 값.
     *
     * ★ 목록에는 늘 다 실린다. 이건 **한 번 더 짚는 줄**이다 — 팔레트와 같은 이유(목록에만 있으면 못 고른다).
     *   "종이 공예처럼 아기자기하게" 에 모델이 theme-bloom 을 골랐다 — 요청문의 "축제" 가 bloom 설명에 있어서다 (Bedrock 실측).
     *   이름이 더 많이 맞는 쪽을 짚어 준다. 동점이면 선언 순서(기본 · 무난한 쪽이 앞)
     */
    public static Optional<Theme> matched(String requestText) {
        record Pick(Theme theme, String cssClass, String desc, boolean core) implements Pickable { }
        List<Pick> picks = blankThemes().stream().map(t -> new Pick(t, t.cssClass, t.desc, false)).toList();
        return KeywordMatch.matched(picks, Layout.withoutLayoutTalk(requestText), 1, Set.of("기본", "일반"), false)
                .stream().findFirst().map(Pick::theme);
    }
}
