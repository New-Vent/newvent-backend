package com.newvent.event.dto.request;

import java.util.Set;
import java.util.regex.Pattern;

import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 제목 글자 크기 · 색 직접 지정.
 *
 * ★★ 왜 모델이 아니라 여기인가 — 글자 크기와 색은 **CSS 가 쥐고 있다.**
 *   모델에게 시키면 class 를 붙이고 "강조했습니다" 라고 보고하는데 화면은 그대로다
 *   (실측: "문구 색을 어둡게" 가 v8 까지 가도 흰색이었다). 돈만 나가고 안 바뀐다.
 *   관리자가 고른 값을 **인라인 style 로 박는 길**만 확실하다 — ButtonStyle 과 같은 수법이다.
 *
 * ★★ 대가가 있다. 인라인 style 을 박으면 그 요소는 **팔레트 · 테마를 더 이상 안 따른다.**
 *   제목을 빨간색으로 지정해 두면 초록 팔레트로 바꿔도 그 제목만 빨갛게 남는다.
 *   그래서 비운 항목은 손대지 않는다 — 비움이 "테마를 따른다" 는 뜻이다.
 *
 * @param titleColor   큰 제목(hero h1) 글자색 (^#[0-9a-fA-F]{6}$)
 * @param titleSize    큰 제목 크기 (small | medium | large | xlarge)
 * @param headingColor 소제목(h2 · .title) 글자색
 * @param headingSize  소제목 크기
 */
@Schema(description = "제목 글자 크기 · 색. 비운 항목은 바꾸지 않고(테마를 따름), 허용하지 않는 값은 400")
public record TextStyle(
        @Schema(description = "큰 제목 글자색 #RRGGBB", example = "#0f172a") String titleColor,
        @Schema(allowableValues = {"small", "medium", "large", "xlarge"}) String titleSize,
        @Schema(description = "소제목 글자색 #RRGGBB", example = "#15803d") String headingColor,
        @Schema(allowableValues = {"small", "medium", "large", "xlarge"}) String headingSize) {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> SIZES = Set.of("small", "medium", "large", "xlarge");

    public TextStyle {
        titleColor = hex(titleColor);
        headingColor = hex(headingColor);
        titleSize = choice(titleSize);
        headingSize = choice(headingSize);
    }

    /** 하나도 안 고른 요청. 서비스가 건너뛸 수 있게 알려 준다 */
    public boolean isEmpty() {
        return titleColor == null && titleSize == null
                && headingColor == null && headingSize == null;
    }

    private static String hex(String value) {
        if (value == null) return null;
        String t = value.trim();
        if (t.isEmpty()) return null;
        if (!HEX_COLOR.matcher(t).matches()) {
            throw new DirectEditException(DirectEditErrorCode.INVALID_BUTTON_STYLE);
        }
        return t.toLowerCase();
    }

    private static String choice(String value) {
        if (value == null) return null;
        String t = value.trim().toLowerCase();
        if (t.isEmpty()) return null;
        if (!SIZES.contains(t)) {
            throw new DirectEditException(DirectEditErrorCode.INVALID_BUTTON_STYLE);
        }
        return t;
    }
}
