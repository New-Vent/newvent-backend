package com.newvent.registry;

import java.util.Arrays;
import java.util.Optional;

/**
 * 템플릿이 쓰는 CSS 테마. **문자열이 아니라 여기서 고른다.**
 *
 * ★ 왜 레지스트리로 옮기는가
 *   같은 매핑이 {@code TemplateResponse.themeOf()} 에 이미 있었다.
 *   거기는 화면용 DTO 라서 HTML 을 만드는 쪽에서는 쓸 수 없었고,
 *   그래서 저장되는 조각에는 테마가 한 번도 들어가지 않았다.
 *   매핑이 두 벌이 되면 언젠가 갈라진다. 한 벌로 둔다.
 */
public enum Theme {

    SPORTS("sports_cheer",          "theme-sports"),
    HOLIDAY("holiday_gift",         "theme-holiday"),
    VIP("member_appreciation",      "theme-vip"),
    SALE("flash_sale",              "theme-sale"),
    LAUNCH("pre_registration",      "theme-launch");

    /** 테마 클래스의 공통 접두사. 이미 테마가 붙었는지 판정할 때 쓴다. */
    public static final String PREFIX = "theme-";

    private final String suffix;
    private final String cssClass;

    Theme(String suffix, String cssClass) {
        this.suffix = suffix;
        this.cssClass = cssClass;
    }

    public String cssClass() {
        return cssClass;
    }

    /**
     * 템플릿 코드로 테마를 고른다. 모르는 코드면 빈 값.
     */
    public static Optional<Theme> of(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) return Optional.empty();
        String k = templateCode.trim();
        return Arrays.stream(values()).filter(t -> k.endsWith(t.suffix)).findFirst();
    }

    /** 화면용 — 모르는 코드면 null. 기존 DTO 의 계약을 그대로 유지한다. */
    public static String cssClassOf(String templateCode) {
        return of(templateCode).map(Theme::cssClass).orElse(null);
    }
}
