package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.newvent.registry.BlockValidator.Failure;

/**
 * 생성 결과에 요청에 없는 수치가 있는지 (#230).
 *
 * 케이스는 생성 시험에서 모델이 실제로 쓴 문구다 — "영화 예매권" 요청에 "추가 데이터 10GB",
 * "쿠폰을 줘" 에 "20,000원", "경품 비교표" 에 "55,000원 → 38,500원".
 */
class ValueCheckGeneratedTest {

    private static List<String> inventedTokens(String html, String source) {
        return ValueCheck.inventedInGenerated(html, source).stream().map(Failure::detail).toList();
    }

    private static String benefit(String name, String desc, String value) {
        return "<section data-block=\"benefits\"><ul><li><div class=\"benefit-name\">" + name
                + "</div><div class=\"benefit-desc\">" + desc + "</div>"
                + (value == null ? "" : "<div class=\"benefit-value\">" + value + "</div>")
                + "</li></ul></section>";
    }

    @Test
    @DisplayName("요청에 없던 혜택의 수치를 잡는다 — 영화 예매권 요청에 '추가 데이터 10GB'")
    void 요청에_없던_수치를_잡는다() {
        String source = "VIP 고객 감사 이벤트\nVIP 고객 감사 이벤트. 영화 예매권을 증정해";
        String html = benefit("추가 데이터 10GB 제공", "다음 달 요금에 적용됩니다.", "10GB")
                + benefit("영화 예매권", "2매를 드립니다.", "2매");

        List<Failure> f = ValueCheck.inventedInGenerated(html, source);

        assertEquals(2, f.size(), f.toString());          // 10GB, 2매 (같은 수치는 한 번만)
        assertTrue(f.stream().allMatch(x -> x.kind() == FailureCode.VALUE_ADDED));
        assertTrue(f.stream().allMatch(x -> x.kind().severity() == FailureCode.Severity.HARD), "재시도 대상이어야 한다");
    }

    @Test
    @DisplayName("요청에 있는 수치는 허용한다")
    void 요청에_있는_수치는_허용한다() {
        String source = "신규 가입 쿠폰\n신규 가입자에게 데이터 쿠폰 3GB를 드려요. 선착순 100명, 30% 할인, 6개월";
        String html = benefit("데이터 3GB 증정", "가입하면 바로 쓸 수 있어요.", "3GB")
                + "<p>선착순 100명에게 30% 할인을 6개월 동안 드립니다.</p>";

        assertEquals(List.of(), inventedTokens(html, source));
    }

    @ParameterizedTest(name = "[{index}] 요청 \"{0}\" ↔ 페이지 \"{1}\"")
    @CsvSource({
            "데이터 3기가를 줘,        데이터 3GB 증정",
            "데이터 3 기가를 줘,       데이터 3GB 증정",
            "퀴즈 3문제를 맞히면,      정답 3개를 맞히면",
            "3만원 상품권,             30000원 상품권",
            "1만 원 할인,              10000원 할인",
            "영화 예매권 2매,          예매권 2장",
            "포인트 1000점을 지급,     1,000포인트 지급",
            "5천원 쿠폰,               5000원 쿠폰",
            "50% 할인,                 50퍼센트 할인",
    })
    @DisplayName("표기만 다른 같은 수치는 지어낸 것이 아니다 — 정상 페이지가 재시도로 몰리면 안 된다")
    void 표기만_다른_같은_수치는_허용한다(String requested, String written) {
        String html = "<section data-block=\"benefits\"><ul><li><div>" + written + "</div></li></ul></section>";

        assertEquals(List.of(), inventedTokens(html, "이벤트\n" + requested), written);
    }

    @Test
    @DisplayName("같은 숫자라도 종류가 다르면 다른 수치다 — 요청의 '10명' 이 '10GB' 를 허용하지 않는다")
    void 종류가_다르면_다른_수치다() {
        String source = "추첨 이벤트\n추첨으로 10명에게 경품을 드려요";
        String html = "<p>선착순 10명, 데이터 10GB, 10% 할인, 10일 동안</p>";

        List<String> invented = inventedTokens(html, source);

        assertEquals(3, invented.size(), invented.toString());     // 10GB · 10% · 10일, 10명은 요청에 있다
    }

    @Test
    @DisplayName("단위가 없는 숫자(연도 · 단계 · 순서)는 보지 않는다")
    void 단위_없는_숫자는_보지_않는다() {
        String html = "<h2>2026 여름 이벤트</h2><ol><li><div>1단계 로그인</div></li><li><div>2단계 응모</div></li></ol>";

        assertEquals(List.of(), inventedTokens(html, "여름 이벤트\n만들어줘"));
    }

    @Test
    @DisplayName("요청에 없는 기간(7일)은 경고로만 — 실패로 만들면 단순한 요청이 4회 재시도 끝에 실패한다 (#230)")
    void 지어낸_기간은_경고다() {
        String html = "<p>가입 후 7일 이내에 사용하세요. 하루 1회만 참여할 수 있어요.</p>";

        List<Failure> f = ValueCheck.inventedInGenerated(html, "가입 이벤트\n가입하면 쿠폰을 줘");

        assertEquals(2, f.size(), f.toString());                        // 7일 · 1회
        Failure period = f.stream().filter(x -> "7일".equals(x.detail())).findFirst().orElseThrow();
        Failure count = f.stream().filter(x -> "1회".equals(x.detail())).findFirst().orElseThrow();
        assertEquals(FailureCode.VALUE_ADDED_PERIOD, period.kind());
        assertTrue(period.isWarning() && !period.isBlocking(), "기간은 재시도 · 실패를 일으키면 안 된다");
        assertEquals(FailureCode.VALUE_ADDED, count.kind());
        assertTrue(count.isBlocking(), "횟수는 약속이라 계속 막는다");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"9월 26일까지", "7일 이내", "3시간 동안", "30분 안에", "2주 뒤", "3개월간"})
    @DisplayName("기간 · 날짜 표현은 모두 경고이고 금액 · 용량 · 할인율 · 수량은 막는다")
    void 기간_단위는_모두_경고다(String phrase) {
        List<Failure> f = ValueCheck.inventedInGenerated("<p>" + phrase + "</p>", "이벤트\n쿠폰을 줘");

        assertEquals(1, f.size(), f.toString());
        assertEquals(FailureCode.VALUE_ADDED_PERIOD, f.get(0).kind(), phrase);

        for (String promise : new String[]{"5000원", "10GB", "20%", "100명", "2매"}) {
            List<Failure> g = ValueCheck.inventedInGenerated("<p>" + promise + "</p>", "이벤트\n쿠폰을 줘");
            assertTrue(g.stream().allMatch(Failure::isBlocking), promise + " 는 막아야 한다");
        }
    }

    @ParameterizedTest(name = "[{index}] 페이지 \"{0}\"")
    @CsvSource({"커피 쿠폰 500분께 드립니다", "커피 쿠폰을 500분에게 드립니다"})
    @DisplayName("'500분께' 는 시간이 아니라 사람 수다 — 요청의 '500명' 과 같은 수치로 본다")
    void 높임말_분은_요청의_명과_같다(String written) {
        assertEquals(List.of(), inventedTokens("<p>" + written + "</p>", "VIP 감사\n커피 쿠폰 500명"));
    }

    @Test
    @DisplayName("요청에 없는 '100분께' 는 지어낸 수량이라 막는다 — 기간 경고로 빠져나가면 안 된다")
    void 높임말_분으로_지어낸_수량은_막는다() {
        List<Failure> f = ValueCheck.inventedInGenerated("<p>추첨으로 100분께 드립니다</p>", "이벤트\n쿠폰을 줘");

        assertEquals(1, f.size(), f.toString());
        assertEquals(FailureCode.VALUE_ADDED, f.get(0).kind());
        assertTrue(f.get(0).isBlocking());
    }

    @Test
    @DisplayName("경고에는 관리자에게 보일 원문이 담긴다 — detail 은 로그 코드라 공백이 _ 로 바뀐다")
    void 경고에_원문이_담긴다() {
        Failure f = ValueCheck.inventedInGenerated("<p>3 시간 동안</p>", "이벤트\n쿠폰을 줘").get(0);

        assertEquals("3 시간", f.subject());
        assertEquals("3_시간", f.detail());
    }

    @Test
    @DisplayName("요청에 있던 기간은 경고도 아니다 — '7일 이내 사용' 이라고 쓰면 그대로 쓴다")
    void 요청에_있는_기간은_허용한다() {
        String source = "월드컵 스코어 맞추기\n스코어를 맞추면 치킨 쿠폰을 주는 이벤트, 7일 이내 사용";

        assertEquals(List.of(), inventedTokens("<p>쿠폰은 7일 이내에 사용하세요.</p>", source));
    }

    @Test
    @DisplayName("혜택이 없는 요청에 만든 숫자 없는 약속은 경고다 — 멤버십 요청에 '두 배 적립 · 전용 할인'")
    void 숫자_없는_약속은_경고한다() {
        String html = "<p>멤버십 포인트 두 배 적립, 프리미엄 고객 전용 특별 할인, 응모 후 당첨자 발표</p>";

        List<Failure> f = ValueCheck.promisesInGenerated(html, "멤버십 이벤트\n고급스럽고 어두운 분위기의 멤버십 이벤트");

        assertTrue(f.size() >= 3, f.toString());
        assertTrue(f.stream().allMatch(x -> x.kind() == FailureCode.VALUE_ADDED_PROMISE && x.isWarning() && !x.isBlocking()));
    }

    @Test
    @DisplayName("요청에 있는 약속은 같은 뜻의 말까지 허용한다 — '예매권을 줘' 의 '증정', '추첨' 의 '당첨'")
    void 요청에_있는_약속은_허용한다() {
        String html = "<p>영화 예매권 증정, 추첨으로 당첨자 발표, 쿠폰 할인</p>";

        List<Failure> f = ValueCheck.promisesInGenerated(html, "이벤트\n영화 예매권을 줘. 추첨해서 쿠폰 할인도 줘");

        assertEquals(List.of(), f);
    }

    @Test
    @DisplayName("요청문을 모르거나 HTML 이 없으면 건너뛴다")
    void 비어_있으면_건너뛴다() {
        assertEquals(List.of(), ValueCheck.inventedInGenerated(null, "x"));
        assertEquals(List.of(), ValueCheck.inventedInGenerated("  ", "x"));
        // 견줄 글이 없으면 수치는 모두 '없는 것' 이다 — 호출하는 쪽(HtmlPolicy)이 요청문을 모를 때 부르지 않는다
        assertEquals(1, ValueCheck.inventedInGenerated("<p>10GB</p>", null).size());
    }
}
