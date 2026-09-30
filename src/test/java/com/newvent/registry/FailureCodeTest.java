package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.registry.BlockValidator.Failure;
import com.newvent.registry.FailureCode.Action;
import com.newvent.registry.FailureCode.Severity;

/**
 * 실패 코드 레지스트리가 자기모순 없이 정의됐는지, 로그에 남는 코드 문자열이 예전과 같은지 본다.
 *
 * ★ 스프링 컨텍스트를 안 띄웁니다. 순수 JUnit 입니다.
 *
 * ★ code() 형식 테스트가 이 리팩터링의 안전장치입니다
 *   llm_call_logs.fail_codes 와 다른 테스트들이 "lost_hero" 같은 문자열을 봅니다.
 *   형식이 조용히 바뀌면 과거 로그와 집계가 끊깁니다.
 */
class FailureCodeTest {

    @Test
    @DisplayName("심각도와 대응이 짝을 이루고 key 가 겹치지 않는다")
    void 레지스트리가_일관적이다() {
        assertDoesNotThrow(FailureCode::assertConsistent);
    }

    @Test
    @DisplayName("짝이 안 맞는 심각도·대응 조합은 거부한다")
    void 잘못된_조합을_거부한다() {
        assertTrue(FailureCode.pairs(Severity.HARD, Action.RETRY));
        assertTrue(FailureCode.pairs(Severity.HARD, Action.ASK_ADMIN));
        assertTrue(FailureCode.pairs(Severity.WARNING, Action.NOTIFY));
        assertTrue(FailureCode.pairs(Severity.SOFT, Action.RECOVERED));

        assertFalse(FailureCode.pairs(Severity.SOFT, Action.RETRY),
                "서버가 이미 고친 것을 재시도하면 모델이 멀쩡한 부분을 고칩니다.");
        assertFalse(FailureCode.pairs(Severity.WARNING, Action.RETRY),
                "통과시키면서 재시도할 수는 없습니다.");
        assertFalse(FailureCode.pairs(Severity.HARD, Action.NOTIFY),
                "막았으면 재시도하거나 되물어야 합니다. 알리기만 하면 결과가 없습니다.");
        assertFalse(FailureCode.pairs(Severity.HARD, Action.RECOVERED));
    }

    @Test
    @DisplayName("★ 코드 문자열이 리팩터링 전과 같다 — 로그·집계가 이 문자열을 본다")
    void 코드_문자열_형식이_그대로다() {
        assertEquals("no_html", Failure.of(FailureCode.NO_HTML, "m").code());
        assertEquals("truncated", Failure.of(FailureCode.TRUNCATED, "m").code());
        assertEquals("router_parse", Failure.of(FailureCode.ROUTER_PARSE, "m").code());
        assertEquals("lost_hero", Failure.of(FailureCode.LOST_BLOCK, "hero", "m").code());
        assertEquals("wrote_notices", Failure.of(FailureCode.WROTE_SERVER_BLOCK, "notices", "m").code());
        assertEquals("slot_lost_period", Failure.of(FailureCode.SLOT_LOST, "period", "m").code());
        assertEquals("item_added_benefits", Failure.of(FailureCode.ITEM_ADDED, "benefits", "m").code());
        assertEquals("class_changed_slot:cta-link",
                Failure.of(FailureCode.CLASS_CHANGED, "slot:cta-link", "m").code());
        assertEquals("value_added_3만원", Failure.of(FailureCode.VALUE_ADDED, "3만원", "m").code());
        assertEquals("warning_value_removed_100_",
                Failure.of(FailureCode.VALUE_REMOVED, "100_", "m").code());
    }

    @Test
    @DisplayName("경고인지 막는 실패인지는 코드 문자열이 아니라 심각도로 정한다")
    void 판정은_심각도로_한다() {
        Failure removed = Failure.of(FailureCode.VALUE_REMOVED, "100_", "m");
        assertTrue(removed.isWarning());
        assertFalse(removed.isBlocking(), "수치 삭제는 경고입니다. 막으면 \"짧게 요약해줘\" 가 영원히 실패합니다.");

        for (FailureCode c : FailureCode.values()) {
            Failure f = Failure.of(c, "m");
            assertEquals(c.severity() == Severity.HARD, f.isBlocking(), c + " 의 isBlocking 이 심각도와 다릅니다.");
            assertEquals(c.severity() == Severity.WARNING, f.isWarning(), c + " 의 isWarning 이 심각도와 다릅니다.");
        }
    }

    @Test
    @DisplayName("코드와 메시지 없이는 만들 수 없다")
    void 필수값이_없으면_거부한다() {
        assertThrows(NullPointerException.class, () -> Failure.of(null, "m"));
        assertThrows(NullPointerException.class, () -> Failure.of(FailureCode.NO_HTML, null));
    }

    @Test
    @DisplayName("검증기가 내는 코드는 전부 레지스트리에서 온다")
    void 검증기_출력이_레지스트리를_쓴다() {
        List<Failure> gen = BlockValidator.validateGenerated("<div><h1>제목</h1></div>");
        assertTrue(gen.stream().anyMatch(f -> f.kind() == FailureCode.NO_SECTION));
        assertTrue(gen.stream().anyMatch(f -> f.kind() == FailureCode.LOST_BLOCK && "hero".equals(f.detail())));

        List<Failure> edit = BlockValidator.validateEdited(Block.HERO,
                "<section data-block=\"hero\"><h1>제목</h1><p data-slot=\"period\"></p></section>",
                "<section data-block=\"hero\"><h1>새 제목</h1></section>");
        assertTrue(edit.stream().anyMatch(f -> f.kind() == FailureCode.SLOT_LOST && "period".equals(f.detail())));
    }

    /**
     * 실패 유형 문서(표)와 코드를 맞출 때 쓴다. 출력만 하고 판정하지 않는다.
     *   ./gradlew test --tests com.newvent.registry.FailureCodeTest
     */
    @Test
    @DisplayName("레지스트리를 문서용 표로 출력한다")
    void 레지스트리_표를_출력한다() {
        StringBuilder md = new StringBuilder("| FailureCode | key | 분류 | 심각도 | 대응 |\n|---|---|---|---|---|\n");
        for (FailureCode c : FailureCode.values()) {
            md.append("| ").append(c.name())
              .append(" | ").append(c.key())
              .append(" | ").append(c.category())
              .append(" | ").append(c.severity())
              .append(" | ").append(c.action())
              .append(" |\n");
        }
        System.out.println(md);

        for (FailureCode c : FailureCode.values()) {
            assertTrue(md.toString().contains("| " + c.key() + " |"));
        }
    }
}
