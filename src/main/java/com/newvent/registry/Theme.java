package com.newvent.registry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
 *   테마 3 × 팔레트 19 가 그대로 조합이 된다. 계절 · 명절은 팔레트의 몫이다 —
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

    // ── 백지 테마 (suffix 없음) ────────────────────────────────────
    //
    // ★ desc 는 프롬프트에 그대로 나간다. 색 이름이 아니라 **성격**을 적는다 —
    //   색은 팔레트가 받는다.
    // ★ BASIC 의 desc 가 "분위기가 분명하지 않으면 이것" 이라고 말하는 게 중요하다.
    //   모델에게 **긍정형 출구**를 준다. 금지문("지어내지 마라")을 쓰면 안 된다 —
    //   v-benefits-carousel 은 프롬프트 전체에서 금지 문장에만 나왔고,
    //   모델이 거기서 이름을 배워 골랐다.
    BASIC(null, "theme-basic", Tone.LIGHT,
            "차분 · 일반 — 분위기가 분명하지 않거나 업무적인 이벤트. 장식 없이 읽기 좋게"),
    BLOOM(null, "theme-bloom", Tone.LIGHT,
            "발랄 · 축제 · 어린이 · 먹거리 — 밝은 배경에 구름, 통통한 외곽선 제목"),
    AURORA(null, "theme-aurora", Tone.DARK,
            "밤 · 프리미엄 · 게임 · 신비 — 어두운 배경에 별가루, 떠 있는 한 장의 카드");

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
}
