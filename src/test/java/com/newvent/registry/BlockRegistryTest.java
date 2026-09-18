package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.infra.llm.LlmClient;
import com.newvent.infra.llm.MockLlmClient;

/**
 * 레지스트리 · 프롬프트 · 검증기가 서로 맞물려 도는지 본다.
 *
 * ★ 스프링 컨텍스트를 안 띄웁니다. 순수 JUnit 이라 빠르고 DB 도 필요 없습니다.
 *
 * 이 테스트가 지키는 것:
 *   블록을 추가·수정했는데 프롬프트나 검증기 한쪽만 고치면 여기서 빨간불이 납니다.
 *   그게 레지스트리를 만든 이유입니다.
 */
class BlockRegistryTest {

    @Test
    @DisplayName("shape 와 must 가 짝을 이룬다")
    void 레지스트리가_일관적이다() {
        assertDoesNotThrow(Block::assertConsistent);
    }

    @Test
    @DisplayName("프롬프트에 모델이 만들 블록이 전부 들어간다")
    void 프롬프트가_모든_블록을_담는다() {
        String system = PromptBuilder.generate();

        for (Block b : Block.llmBlocks()) {
            assertTrue(system.contains(b.key()),
                    b.key() + " 가 프롬프트에 없습니다. PromptBuilder 가 레지스트리를 안 읽고 있습니다.");
        }
        for (Block b : Block.serverBlocks()) {
            assertTrue(system.contains(b.key()),
                    b.key() + " 를 만들지 말라는 말이 프롬프트에 없습니다. 모델이 만들어버립니다.");
        }
    }

    @Test
    @DisplayName("SERVER 블록은 수정 프롬프트를 만들 수 없다")
    void 서버블록은_수정_대상이_아니다() {
        for (Block b : Block.serverBlocks()) {
            assertThrows(IllegalArgumentException.class, () -> PromptBuilder.edit(b),
                    b.key() + " 는 서버가 채우는데 수정 프롬프트가 만들어졌습니다.");
            assertFalse(b.canEdit(), b.key() + " 가 수정 가능으로 나옵니다.");
        }
    }

    @Test
    @DisplayName("정상 출력은 검증을 통과한다")
    void 정상_HTML은_통과한다() {
        LlmClient llm = new MockLlmClient(0);
        String html = llm.chat(LlmClient.Request.html(PromptBuilder.generate(), "여름 데이터 이벤트")).content();

        List<BlockValidator.Failure> fails = BlockValidator.validateGenerated(html);
        assertTrue(fails.isEmpty(), "통과해야 하는데 걸렸습니다: " + fails);
    }

    @Test
    @DisplayName("깨진 출력은 empty_benefits 로 잡힌다")
    void 깨진_HTML은_걸린다() {
        LlmClient llm = new MockLlmClient(0);
        String html = llm.chat(LlmClient.Request.html(PromptBuilder.generate(), "FAIL")).content();

        List<BlockValidator.Failure> fails = BlockValidator.validateGenerated(html);
        assertTrue(fails.stream().anyMatch(f -> f.code().equals("empty_benefits")),
                "empty_benefits 를 못 잡았습니다: " + fails);
    }

    @Test
    @DisplayName("sanitize 가 script 를 지우고 style 은 남긴다")
    void 정화가_스크립트를_지운다() {
        String dirty = """
                <section data-block="hero" style="color:red">
                  <h1>제목</h1>
                  <script>alert(1)</script>
                </section>""";
        String clean = BlockValidator.sanitize(dirty);

        assertFalse(clean.contains("<script"), "script 가 남았습니다: " + clean);
        assertFalse(clean.contains("alert"),   "script 내용이 남았습니다: " + clean);
        assertTrue(clean.contains("data-block"), "data-block 이 지워졌습니다. 블록을 못 찾게 됩니다.");
        assertTrue(clean.contains("style"),      "style 이 지워졌습니다. 색 변경이 불가능해집니다.");
    }

    @Test
    @DisplayName("merge 는 대상 블록만 갈아끼운다")
    void 병합이_대상만_바꾼다() {
        String doc = """
                <section data-block="hero"><h1>옛 제목</h1></section>
                <section data-block="cta"><a href="#" class="btn">참여</a></section>""";
        String incoming = "<section data-block=\"hero\"><h1>새 제목</h1></section>";

        String merged = BlockValidator.merge(doc, Block.HERO, incoming);

        assertTrue(merged.contains("새 제목"),  "hero 가 안 바뀌었습니다.");
        assertFalse(merged.contains("옛 제목"), "옛 hero 가 남았습니다.");
        assertTrue(merged.contains("참여"),     "건드리면 안 되는 cta 가 사라졌습니다.");
    }

    @Test
    @DisplayName("라우터 프롬프트에 모든 영역 이름이 들어간다")
    void 라우터가_모든_영역을_안다() {
        String router = PromptBuilder.router();

        for (Block b : Block.values()) {
            assertTrue(router.contains(b.key()),
                    b.key() + " 가 라우터 프롬프트에 없습니다. 그 영역은 분류가 안 됩니다.");
        }
    }

    @Test
    @DisplayName("CSS 정화 — 허용 속성만 남는다")
    void CSS_정화가_위험한_속성을_지운다() {
        String dirty = """
                <section data-block="hero" style="color:red; position:fixed; z-index:9999; background-image:url(javascript:alert(1))">
                  <h1>제목</h1>
                </section>""";
        String clean = BlockValidator.sanitize(dirty);

        assertTrue(clean.contains("color:red"),   "허용 속성 color 가 지워졌습니다: " + clean);
        assertFalse(clean.contains("position"),   "position 이 남았습니다. 화면을 덮을 수 있습니다: " + clean);
        assertFalse(clean.contains("z-index"),    "z-index 가 남았습니다: " + clean);
        assertFalse(clean.contains("javascript"), "javascript: 가 남았습니다: " + clean);
    }

    @Test
    @DisplayName("모델이 data-slot 을 만들어도 제거된다")
    void 슬롯은_모델이_못_만든다() {
        String dirty = "<section data-block=\"hero\"><span data-slot=\"period\">가짜 기간</span></section>";

        String clean = BlockValidator.sanitize(dirty);

        assertFalse(clean.contains("data-slot"),
                "data-slot 이 남았습니다. 슬롯은 서버만 심어야 합니다: " + clean);
    }

    @Test
    @DisplayName("슬롯 선택자는 레지스트리에서 나온다")
    void 슬롯_선택자() {
        assertEquals("[data-slot=\"period\"]",   Slot.PERIOD.selector());
        assertEquals("[data-slot=\"cta-link\"]", Slot.CTA_LINK.selector());
        assertThrows(IllegalArgumentException.class, () -> Slot.of("없는슬롯"));
    }
}
