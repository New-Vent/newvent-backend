package com.newvent.registry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;

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
            "\\d[\\d,]*(?:\\.\\d+)?\\s*(?:천만|천|만|억)?\\s*원(?![A-Za-z])"
            // 금액 외 숫자+단위
            + "|\\d[\\d,]*(?:\\.\\d+)?\\s*(?:시간|일|분|초|회|개월?|달|배|명|장|점|종|GB|MB|KB|TB|GHz|Mbps|Gbps)(?![A-Za-z])"
            // 숫자 + %
            + "|\\d[\\d,]*(?:\\.\\d+)?\\s*%",
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
                f.add(Failure.of(FailureCode.VALUE_ADDED, sanitizeCode(t),
                        "\"" + t + "\" 는 원본에 없던 수치입니다. "
                        + "요청받지 않은 약속이 생기면 사고가 됩니다. "
                        + "원본에 있던 수치만 쓰거나, 지어낸 수치를 빼세요."));
            }
        }

        // 있던 숫자가 빠졌다 — 경고(실패 아님)
        for (String t : tokensBefore) {
            if (!valuesAfter.contains(normalize(t))) {
                f.add(Failure.of(FailureCode.VALUE_REMOVED, sanitizeCode(t),
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
        // 인라인 강조 태그는 수치를 끊지 않고, HTML 엔티티는 실제 문자로 읽는다.
        var body = Jsoup.parseBodyFragment(text).body();
        body.select("script, style").remove();
        String plain = body.text();
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
        token = token.replaceAll("\\s+", "");
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

    // ── 생성: 요청에 없는 수치 ────────────────────────────────────────

    /**
     * 생성 결과의 수치를 요청과 견주는 토큰. {@link #TOKEN} 보다 단위가 많다 — 요청문은 "3기가", "퀴즈 3문제", "2매" 처럼
     * 쓰고 모델은 "3GB", "3개" 로 바꿔 쓰기 때문이다. 표기만 다른 같은 수치를 지어낸 것으로 보면 정상 페이지가 재시도로 몰린다.
     */
    private static final Pattern GEN_TOKEN = Pattern.compile(
            "\\d[\\d,]*(?:\\.\\d+)?\\s*(?:천만|천|만|억)?\\s*원(?![A-Za-z])"
            + "|\\d[\\d,]*(?:\\.\\d+)?\\s*(?:시간|일|분|초|주|회|개월?|달|배|명|장|점|종|매|건|가지|문제|포인트"
            + "|기가바이트|메가바이트|기가|메가|GB|MB|KB|TB|GHz|Mbps|Gbps)(?![A-Za-z])"
            + "|\\d[\\d,]*(?:\\.\\d+)?\\s*(?:%|퍼센트|P(?![A-Za-z]))",
            Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern GEN_PARTS = Pattern.compile("^(\\d+(?:\\.\\d+)?)(천만|천|만|억)?(.*)$");

    /**
     * 생성 결과에 **제목 · 요청문 · 기간에 없는 수치**가 있는지 본다.
     *
     * ★ 왜 — 생성 시험에서 22개 페이지 중 19개가 요청에 없는 수치("영화 예매권 2매", "추가 데이터 10GB",
     *   "월 요금 44,000원 → 33,000원", "참여만 해도 100% 당첨")를 담았다(#230). 프롬프트의 "지어내지 마라" 만으로는
     *   못 막았고, 검증기는 연도만 봤다({@link BlockValidator#validateGenerated}). 수정 경로는 이미
     *   {@link #diff} 로 막고 있다 — 생성에도 같은 사고 기준을 쓴다.
     * ★ 수치는 (종류, 값)으로 견준다. 3기가 = 3GB, 3문제 = 3개, 3만원 = 30,000원.
     *   종류가 다르면 같은 숫자여도 다르다 — 요청의 "10명" 이 "10GB" 를 허용하지 않는다.
     * ★ 단위가 붙은 수치만 본다. 연도 · 순서 번호 · 단계 번호는 오탐이 커서 보지 않는다(위 {@link #TOKEN} 과 같은 선택).
     *
     * @param html   모델이 쓴 영역들(유의사항 등 서버 영역은 아직 없다)
     * @param source 제목 · 요청문 · 기간을 이은 글
     * @return 요청에 없는 수치마다 {@code VALUE_ADDED}(재시도). 없으면 빈 목록
     */
    public static List<Failure> inventedInGenerated(String html, String source) {
        if (html == null || html.isBlank()) return List.of();
        Set<String> allowed = new LinkedHashSet<>();
        Matcher sm = GEN_TOKEN.matcher(source == null ? "" : source);
        while (sm.find()) allowed.add(genKey(sm.group()));

        var body = Jsoup.parseBodyFragment(html).body();
        body.select("script, style").remove();
        List<Failure> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = GEN_TOKEN.matcher(body.text());
        while (m.find()) {
            String token = m.group().strip();
            if (allowed.contains(genKey(token)) || !seen.add(genKey(token))) continue;
            // 기간 · 날짜는 실패가 아니라 경고다 — 모델이 4회 연속 다시 써서 단순한 요청이 실패로 끝났다(#230 종단 측정).
            if (isPeriod(genKey(token))) {
                out.add(Failure.of(FailureCode.VALUE_ADDED_PERIOD, sanitizeCode(token),
                        "\"" + token + "\" 는 요청문에 없는 기간입니다."));
                continue;
            }
            out.add(Failure.of(FailureCode.VALUE_ADDED, sanitizeCode(token),
                    "\"" + token + "\" 는 요청문에 없는 수치입니다. 요청받지 않은 약속이 생기면 사고가 됩니다. "
                    + "요청문에 적힌 수치만 쓰고, 값을 모르면 그 문구를 아예 빼세요."));
        }
        return out;
    }

    /**
     * 숫자 없는 약속의 말 묶음 (#230). 앞의 말이 페이지에 있는데 묶음 어느 말도 요청에 없으면 지어낸 약속으로 본다.
     * 묶음 안은 같은 뜻으로 쳐서 "예매권을 줘" 요청의 "증정" 은 허용한다 — 정상 페이지에 경고가 붙으면 알림이 무뎌진다.
     */
    private static final String[][] PROMISE_GROUPS = {
            {"추첨,당첨,응모,뽑", "당첨"},
            {"경품,사은품,상품권,선물,증정,지급,제공,드려,드립,드려요,줘,줄게,준다,주는", "경품,사은품,상품권"},
            {"할인,특가,세일,저렴", "할인"},
            {"적립,포인트,캐시백,마일리지", "적립,캐시백,두 배"},
            {"쿠폰", "쿠폰"},
            {"무료,공짜,0원", "무료"},
            {"선착순,한정", "선착순"},
            {"전용,VIP,회원,고객,가입자,신규", "전용,VIP,초청,고객에게만"},
    };

    /**
     * 생성 결과에 요청에 없던 **숫자 없는 약속**(추첨 · 경품 · 할인 · 적립 · 쿠폰 · 무료 · 선착순 · 전용 자격)이 있는지 본다.
     *
     * ★ 왜 — 수치를 막아도 모델은 혜택이 없는 요청에 "포인트 두 배 적립", "프리미엄 고객 전용 할인", "응모 → 당첨자 발표" 를 채웠다.
     *   숫자가 없어 {@link #inventedInGenerated} 가 못 잡는다.
     * ★ **경고만** 한다. 단어 검사라 "추첨" 요청의 "당첨" 같은 말이 오탐될 수 있고, 막으면 정상 요청이 재시도로 몰린다(기간과 같은 결정).
     */
    public static List<Failure> promisesInGenerated(String html, String source) {
        if (html == null || html.isBlank()) return List.of();
        String src = source == null ? "" : source;
        String text = Jsoup.parseBodyFragment(html).body().text();
        List<Failure> out = new ArrayList<>();
        for (String[] g : PROMISE_GROUPS) {
            boolean requested = false;
            for (String w : g[0].split(",")) if (src.contains(w)) { requested = true; break; }
            if (requested) continue;
            for (String w : g[1].split(",")) {
                if (text.contains(w)) {
                    out.add(Failure.of(FailureCode.VALUE_ADDED_PROMISE, sanitizeCode(w),
                            "\"" + w + "\" 는 요청문에 없는 약속입니다."));
                    break;
                }
            }
        }
        return out;
    }

    /** 기간 · 날짜 단위의 키인가 — 시간 · 일 · 분 · 초 · 주 · 개월 */
    private static boolean isPeriod(String key) {
        return key.startsWith("시간:") || key.startsWith("일:") || key.startsWith("분:")
                || key.startsWith("초:") || key.startsWith("주:") || key.startsWith("개월:");
    }

    /** 수치를 (종류, 값) 키로 바꾼다 — 표기 차이(기가/GB, 문제/개, 만원/원)를 없앤다 */
    static String genKey(String token) {
        String t = token.replaceAll("\\s+", "").replace(",", "");
        Matcher m = GEN_PARTS.matcher(t);
        if (!m.matches()) return t;
        BigDecimal n = new BigDecimal(m.group(1));
        String mulUnit = m.group(2);
        String rest = m.group(3);
        if (rest.equals("원")) {
            long mul = mulUnit == null ? 1L : switch (mulUnit) {
                case "천" -> 1_000L;
                case "만" -> 10_000L;
                case "천만" -> 10_000_000L;
                default -> 100_000_000L;
            };
            return "원:" + n.multiply(BigDecimal.valueOf(mul)).stripTrailingZeros().toPlainString();
        }
        // "천 · 만 · 억" 은 금액에만 붙는다. 다른 단위 앞이면 읽은 그대로 둔다
        String num = n.stripTrailingZeros().toPlainString() + (mulUnit == null ? "" : mulUnit);
        return switch (rest) {
            case "GB", "기가", "기가바이트" -> "GB:" + num;
            case "MB", "메가", "메가바이트" -> "MB:" + num;
            case "%", "퍼센트" -> "%:" + num;
            case "개월", "달" -> "개월:" + num;
            case "명", "개", "장", "점", "종", "회", "배", "매", "건", "가지", "문제", "P", "포인트" -> "수량:" + num;
            default -> rest + ":" + num;
        };
    }

    /** Failure.code 에 쓸 수 없는 문자를 제거합니다 */
    private static String sanitizeCode(String token) {
        return token.replaceAll("[^a-zA-Z0-9가-힣_\\-]", "_");
    }
}
