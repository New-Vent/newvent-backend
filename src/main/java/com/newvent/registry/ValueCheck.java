package com.newvent.registry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.newvent.registry.BlockValidator.Failure;

/**
 * REQ-LLM-41 — 값 변조 감지기.
 *
 * ★ 이 파일이 막는 사고
 *   "혜택 문구를 더 크고 파격적으로"
 *     → `구매 금액 10% 페이백`  →  `최대 3만원 페이백`
 *   관리자가 모르는 금액 약속이 생깁니다. 문구 오류가 아니라 사고입니다.
 *
 * ★ 원칙 — 주어지지 않은 값은 만들지 않는다 (PromptBuilder.지어내기_금지() 와 같음)
 *   요청문에 이미 나온 숫자라면 모델이 그걸 쓴 것이므로 허용합니다.
 *   요청문에 없는데 출력에 새로 생긴 숫자만 잡습니다.
 *   → 오탐을 크게 줄입니다.
 *
 * ★ 추가와 삭제를 다르게 처리합니다
 *   value_added   없던 숫자가 생김  → Failure (실패)
 *   value_removed 있던 숫자가 빠짐  → Failure (경고 — code 에 "warning_" 접두어)
 *
 *   "짧게 요약해줘" 에 숫자가 빠지는 건 요청을 이행한 결과일 수 있습니다.
 *   실제 v11 에서 `100% 캐시미어` → `캐시미어` 가 그런 경우였습니다.
 *   없던 금액이 생기는 것만 사고입니다.
 *
 * ★ 숫자+단위 토큰 정의
 *   10%, 10GB, 3만원, 0원, 72시간, 1,000원, 30일 같은 패턴을 잡습니다.
 *   순수 숫자(연도·순서)는 의도적으로 잡지 않습니다 — 오탐을 줄이는 선택입니다.
 *   단위가 붙은 것만 "약속된 값"으로 봅니다.
 */
public final class ValueCheck {

    private ValueCheck() {}

    /**
     * 숫자+단위 토큰을 잡는 정규식.
     *
     * 잡는 것:
     *   10%, 20GB, 3만원, 0원, 72시간, 1,000원, 30일, 5회, 3배, 50MB, 12개월
     *
     * 잡지 않는 것:
     *   순수 숫자 단독(1, 2026, 100) — 연도·순서 번호와 구별이 어려워 오탐이 크다.
     *   %·GB·원 같은 단위가 붙어야 "약속된 값"으로 본다.
     *
     * 한국어 단위: 원, 천원, 만원, 천만원, 억원, 시간, 일, 분, 초, 회, 개, 개월, 달, 배, 명, 장, 점, 종
     * 영문 단위: % (앞에 붙는 것), GB, MB, KB, TB, GHz, Mbps, Gbps
     */
    private static final Pattern TOKEN = Pattern.compile(
            // 금액은 천/만/천만/억 및 '1만 원'처럼 원 앞의 공백을 허용한다.
            "\\d[\\d,]*(?:\\.\\d+)?(?:천만|천|만|억)?\\s*원(?![A-Za-z])"
            // 금액 외 숫자+단위
            + "|\\d[\\d,]*(?:\\.\\d+)?(?:시간|일|분|초|회|개월?|달|배|명|장|점|종|GB|MB|KB|TB|GHz|Mbps|Gbps)(?![A-Za-z])"
            // 숫자 + %
            + "|\\d[\\d,]*(?:\\.\\d+)?%",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private static final Pattern MONEY = Pattern.compile(
            "^(\\d[\\d,]*(?:\\.\\d+)?)(천만|천|만|억)?\\s*원$"
    );

    /**
     * 수정 전후의 숫자+단위 토큰을 비교합니다.
     *
     * @param before     수정 전 블록 HTML
     * @param after      수정 후 블록 HTML (sanitizeEdited 를 거친 것)
     * @param userPrompt 사용자가 보낸 요청문 (여기에 나온 숫자는 허용)
     * @return 실패·경고 목록. 빈 목록이면 통과.
     * 같은 수치가 어떤 혜택에 연결되는지는 비교하지 않으므로 혜택 간 수치 교환은 검출하지 못합니다.
     */
    public static List<Failure> diff(String before, String after, String userPrompt) {
        Set<String> tokensBefore = extract(before);
        Set<String> tokensAfter  = extract(after);
        Set<String> tokensInReq  = extract(userPrompt == null ? "" : userPrompt);
        Set<String> valuesBefore = normalized(tokensBefore);
        Set<String> valuesAfter = normalized(tokensAfter);
        Set<String> valuesInReq = normalized(tokensInReq);

        List<Failure> f = new ArrayList<>();

        // 없던 숫자가 생겼다 — 요청문에 있던 것은 허용
        for (String t : tokensAfter) {
            String value = normalize(t);
            if (!valuesBefore.contains(value) && !valuesInReq.contains(value)) {
                f.add(new Failure(
                        "value_added_" + sanitizeCode(t),
                        "\"" + t + "\" 는 원본에 없던 수치입니다. "
                        + "요청받지 않은 약속이 생기면 사고가 됩니다. "
                        + "원본에 있던 수치만 쓰거나, 지어낸 수치를 빼세요."));
            }
        }

        // 있던 숫자가 빠졌다 — 경고(실패 아님)
        for (String t : tokensBefore) {
            if (!valuesAfter.contains(normalize(t))) {
                f.add(new Failure(
                        "warning_value_removed_" + sanitizeCode(t),
                        "\"" + t + "\" 가 수정 후 빠졌습니다. "
                        + "의도한 변경인지 확인하세요. (자동 실패는 아닙니다)"));
            }
        }

        return f;
    }

    /**
     * 텍스트에서 숫자+단위 토큰 집합을 추출합니다.
     * HTML 태그는 먼저 제거하고 추출합니다.
     */
    static Set<String> extract(String text) {
        if (text == null || text.isBlank()) return Set.of();
        // HTML 태그 제거 (단순 치환, Jsoup 없이)
        String plain = text.replaceAll("<[^>]*>", " ");
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = TOKEN.matcher(plain);
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    /** 금액만 원 단위로 바꾸고, 다른 단위의 비교 방식은 유지합니다. */
    private static Set<String> normalized(Set<String> tokens) {
        Set<String> values = new LinkedHashSet<>();
        for (String token : tokens) values.add(normalize(token));
        return values;
    }

    private static String normalize(String token) {
        Matcher money = MONEY.matcher(token);
        if (!money.matches()) return token;

        long multiplier;
        String unit = money.group(2);
        if ("천".equals(unit)) multiplier = 1_000L;
        else if ("만".equals(unit)) multiplier = 10_000L;
        else if ("천만".equals(unit)) multiplier = 10_000_000L;
        else if ("억".equals(unit)) multiplier = 100_000_000L;
        else multiplier = 1L;

        return "KRW:" + new BigDecimal(money.group(1).replace(",", ""))
                .multiply(BigDecimal.valueOf(multiplier))
                .stripTrailingZeros().toPlainString();
    }

    /** Failure.code 에 쓸 수 없는 문자를 제거합니다 */
    private static String sanitizeCode(String token) {
        return token.replaceAll("[^a-zA-Z0-9가-힣_\\-]", "_");
    }
}
