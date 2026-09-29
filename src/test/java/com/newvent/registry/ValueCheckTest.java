package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.registry.BlockValidator.Failure;

/**
 * ValueCheck — REQ-LLM-41 값 변조 감지기 테스트.
 *
 * 스프링 · DB 없이 돕니다.
 *
 * 완료 조건:
 *   V1) "더 크고 파격적으로" 에 3만원 생기면 value_added
 *   V2) "10GB 를 20GB 로" 는 통과
 *   V3) 100% 캐시미어 → 캐시미어 는 경고 (실패 아님)
 */
class ValueCheckTest {

    // ── 토큰 추출 기본 동작 ───────────────────────────────────────

    @Test
    @DisplayName("단위 없는 순수 숫자는 잡지 않는다")
    void 순수_숫자는_토큰_아님() {
        Set<String> tokens = ValueCheck.extract("2026년 3월 1일부터 30일까지");
        // 날짜 숫자는 잡지 않는다
        assertFalse(tokens.contains("2026"), "연도를 잡으면 오탐이 커집니다: " + tokens);
        assertFalse(tokens.contains("3"),    "월 숫자를 잡으면 오탐이 커집니다: " + tokens);
    }

    @Test
    @DisplayName("단위 있는 숫자는 토큰으로 잡는다")
    void 단위_붙은_숫자는_토큰() {
        Set<String> tokens = ValueCheck.extract("10GB, 20%, 3만원, 72시간, 1,000원");
        assertTrue(tokens.contains("10GB"),    "10GB 를 못 잡았습니다: " + tokens);
        assertTrue(tokens.contains("20%"),     "20% 를 못 잡았습니다: " + tokens);
        assertTrue(tokens.contains("3만원"),   "3만원 을 못 잡았습니다: " + tokens);
        assertTrue(tokens.contains("72시간"),  "72시간 을 못 잡았습니다: " + tokens);
        assertTrue(tokens.contains("1,000원"), "1,000원 을 못 잡았습니다: " + tokens);
    }

    @Test
    @DisplayName("천원·천만원·원 앞 공백이 있는 금액도 추출한다")
    void 천원_천만원_공백_금액_추출() {
        Set<String> tokens = ValueCheck.extract("3천원, 5천만원, 1만 원");
        assertTrue(tokens.containsAll(Set.of("3천원", "5천만원", "1만 원")),
                "금액을 놓쳤습니다: " + tokens);
    }

    @Test
    @DisplayName("새로 추가된 천원·천만원·공백 포함 금액은 각각 차단한다")
    void 새로_추가된_금액_표기_차단() {
        for (String amount : List.of("3천원", "5천만원", "1만 원")) {
            List<Failure> failures = ValueCheck.diff(
                    "<p>참여해 주세요</p>", "<p>최대 " + amount + " 지급</p>", "혜택 문구를 강조해줘");
            assertTrue(failures.stream().anyMatch(f -> f.isBlocking() && f.message().contains(amount)),
                    amount + " 추가를 막지 못했습니다: " + failures);
        }
    }

    @Test
    @DisplayName("동일 금액의 만원·숫자 표기 변경은 추가나 삭제가 아니다")
    void 같은_금액_표기_변경은_통과() {
        for (String[] pair : List.of(
                new String[] {"1만원", "10,000원"},
                new String[] {"10,000원", "1만 원"},
                new String[] {"3천원", "3,000원"},
                new String[] {"5천만원", "50,000,000원"})) {
            assertTrue(ValueCheck.diff("<p>" + pair[0] + "</p>",
                    "<p>" + pair[1] + "</p>", "문구를 자연스럽게").isEmpty(),
                    pair[0] + " → " + pair[1] + " 는 같은 금액입니다");
        }
    }

    @Test
    @DisplayName("요청문과 수정본에서 동일 금액을 다르게 표기해도 허용한다")
    void 요청한_금액의_다른_표기는_허용() {
        List<Failure> failures = ValueCheck.diff("<p>혜택 없음</p>",
                "<p>20,000원 지급</p>", "2만 원을 지급해줘");
        assertTrue(failures.isEmpty(), "요청한 금액인데 차단됐습니다: " + failures);
    }

    @Test
    @DisplayName("한글 조사가 붙어도 수치를 잡고, 영문 단위 일부는 잡지 않는다")
    void 조사_붙은_수치도_토큰() {
        Set<String> tokens = ValueCheck.extract("3만원을 지급하고 72시간동안 참여, 10GB를 제공. 20GBps는 제외");
        assertTrue(tokens.contains("3만원"), "3만원을 못 잡았습니다: " + tokens);
        assertTrue(tokens.contains("72시간"), "72시간동안 못 잡았습니다: " + tokens);
        assertTrue(tokens.contains("10GB"), "10GB를 못 잡았습니다: " + tokens);
        assertFalse(tokens.contains("20GB"), "20GBps를 20GB로 오인했습니다: " + tokens);
    }

    @Test
    @DisplayName("HTML 태그 안 숫자도 추출한다")
    void HTML_내부_숫자도_추출() {
        Set<String> tokens = ValueCheck.extract(
                "<li class=\"benefit\">최대 <strong>10%</strong> 페이백</li>");
        assertTrue(tokens.contains("10%"), "HTML 태그 안 수치를 못 잡았습니다: " + tokens);
    }

    // ── V1: "더 크고 파격적으로" → 3만원 생김 ─────────────────────

    /**
     * ★ 핵심 시나리오 (v11 벤치마크 실제 사례)
     *   요청문: "혜택 문구를 더 크고 파격적으로"
     *   원본  : 구매 금액 10% 페이백
     *   출력  : 최대 3만원 페이백   ← 3만원 이 새로 생김
     *
     *   요청문에 3만원 이 없으므로 value_added 로 잡혀야 합니다.
     */
    @Test
    @DisplayName("★ V1 — 없던 금액이 생기면 value_added 실패")
    void V1_없던_금액이_생기면_실패() {
        String before = "<li>구매 금액 10% 페이백</li>";
        String after  = "<li>최대 3만원 페이백</li>";
        String prompt = "혜택 문구를 더 크고 파격적으로";

        List<Failure> fails = ValueCheck.diff(before, after, prompt);

        assertTrue(
                fails.stream().anyMatch(f -> f.code().startsWith("value_added_")),
                "3만원 이 생겼는데 value_added 가 없습니다: " + fails);

        // 실패 메시지에 수치가 나와야 한다
        String msg = fails.stream()
                .filter(f -> f.code().startsWith("value_added_"))
                .findFirst().orElseThrow().message();
        assertTrue(msg.contains("3만원"), "실패 메시지에 어느 수치인지 안 나옵니다: " + msg);
    }

    @Test
    @DisplayName("금액 뒤에 조사가 붙어도 요청하지 않은 수치 추가를 막는다")
    void V1_조사_붙은_새_금액도_실패() {
        List<Failure> fails = ValueCheck.diff(
                "<p>구매 금액 10% 페이백</p>",
                "<p>최대 3만원을 돌려드립니다</p>",
                "혜택 문구를 더 크게");

        assertTrue(fails.stream().anyMatch(f -> f.code().startsWith("value_added_")
                && f.message().contains("3만원")));
    }

    // ── V2: "10GB 를 20GB 로" 는 통과 ────────────────────────────

    /**
     * 요청문에 20GB 가 명시돼 있으므로 모델이 그걸 쓴 것 → 허용해야 합니다.
     * false positive 를 줄이는 핵심 장치입니다.
     */
    @Test
    @DisplayName("★ V2 — 요청문에 나온 숫자로 바꾸면 통과")
    void V2_요청문에_있는_숫자는_통과() {
        String before = "<li>기본 제공 데이터 10GB</li>";
        String after  = "<li>기본 제공 데이터 20GB</li>";
        String prompt = "10GB 를 20GB 로 바꿔줘";

        List<Failure> fails = ValueCheck.diff(before, after, prompt);

        // value_added 가 없어야 한다 (20GB 는 요청문에서 온 것)
        boolean hasValueAdded = fails.stream()
                .anyMatch(f -> f.code().startsWith("value_added_"));
        assertFalse(hasValueAdded,
                "요청문에 있는 20GB 가 value_added 로 걸렸습니다: " + fails);
    }

    // ── V3: 100% 캐시미어 → 캐시미어 (경고, 실패 아님) ────────────

    /**
     * "짧게 요약해줘" 같은 요청에서 숫자가 빠지는 건 의도한 결과일 수 있습니다.
     * 실제 v11 사례: 100% 캐시미어 → 캐시미어 (요청: 짧게)
     * warning_value_removed 는 있어도 value_added 는 없어야 합니다.
     * 그리고 실패(value_added) 는 없어야 전체 검증이 통과합니다.
     */
    @Test
    @DisplayName("★ V3 — 숫자가 빠지면 경고만 (실패 아님)")
    void V3_숫자가_빠지면_경고만() {
        String before = "<li>100% 캐시미어 소재</li>";
        String after  = "<li>캐시미어 소재</li>";
        String prompt = "짧게 요약해줘";

        List<Failure> fails = ValueCheck.diff(before, after, prompt);

        // value_added 는 없어야 한다
        assertFalse(
                fails.stream().anyMatch(f -> f.code().startsWith("value_added_")),
                "숫자를 뺐는데 value_added 가 나왔습니다: " + fails);

        // warning_value_removed 는 있어야 한다 — 경고를 통해 관리자가 확인 가능
        assertTrue(
                fails.stream().anyMatch(f -> f.code().startsWith("warning_value_removed_")),
                "100% 가 빠졌는데 경고도 없습니다: " + fails);
        assertTrue(fails.stream().allMatch(Failure::isWarning),
                "수치 삭제만 했는데 실패가 들어왔습니다: " + fails);
    }

    // ── 추가 케이스 ───────────────────────────────────────────────

    @Test
    @DisplayName("원본과 동일하면 아무 실패도 없다")
    void 변경_없으면_통과() {
        String html = "<li>구매 금액 10% 페이백</li>";

        List<Failure> fails = ValueCheck.diff(html, html, "문구를 자연스럽게");

        assertTrue(fails.isEmpty(), "변경이 없는데 실패가 났습니다: " + fails);
    }

    @Test
    @DisplayName("여러 수치가 동시에 바뀌어도 각각 잡힌다")
    void 여러_수치_동시_변경() {
        String before = "<p>10% 할인, 최대 5,000원</p>";
        String after  = "<p>20% 할인, 최대 1만원</p>";
        String prompt = "할인율을 20%로 올려줘";

        List<Failure> fails = ValueCheck.diff(before, after, prompt);

        // 20% 는 요청문에 있으므로 통과
        assertFalse(fails.stream().anyMatch(f ->
                f.code().startsWith("value_added_") && f.code().contains("20_")),
                "요청문에 있는 20% 가 value_added 로 걸렸습니다: " + fails);

        // 1만원 은 요청문에 없으므로 value_added
        assertTrue(fails.stream().anyMatch(f ->
                f.code().startsWith("value_added_") && f.message().contains("1만원")),
                "1만원 이 생겼는데 value_added 가 없습니다: " + fails);
    }

    @Test
    @DisplayName("userPrompt 가 null 이어도 NPE 없이 작동한다")
    void userPrompt_null_안전() {
        String before = "<li>혜택 없음</li>";
        String after  = "<li>10% 할인</li>";

        assertDoesNotThrow(() -> ValueCheck.diff(before, after, null),
                "userPrompt null 에서 NPE 가 났습니다");

        List<Failure> fails = ValueCheck.diff(before, after, null);
        assertTrue(fails.stream().anyMatch(f -> f.code().startsWith("value_added_")),
                "null 프롬프트에서 새 수치를 못 잡았습니다: " + fails);
    }
}
