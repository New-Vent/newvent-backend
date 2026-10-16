package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.generation.service.ResourceTemplateLoader;
import com.newvent.generation.service.TemplateLoader;
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
 *
 * ★ 슬롯 관련은 SlotPreservationTest 로 옮겼습니다.
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
    @DisplayName("정화가 script 를 지우고 style 은 남긴다")
    void 정화가_스크립트를_지운다() {
        String dirty = """
                <section data-block="hero" style="color:red">
                  <h1>제목</h1>
                  <script>alert(1)</script>
                </section>""";
        String clean = BlockValidator.sanitizeGenerated(dirty);

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
    @DisplayName("라우터 프롬프트 — 페이지 전체 색감 요청은 hero 하나로 보내라고 안내하고, 되돌리기 예시가 있다")
    void 라우터가_페이지_전체_색감을_hero_하나로_안내한다() {
        String router = PromptBuilder.router();

        // 이 규칙이 빠지면 "원래 색으로 되돌려줘" · "전체를 노랑으로" 에 모델이 14개 영역 전부에 STYLE 을 내고,
        // RouteParser 가 MAX_OPS(3) 초과로 버려서 "요청을 이해하지 못했습니다" 가 나온다.
        assertTrue(router.contains("영역을 나열하지 않는다. hero 하나만 쓴다."),
                "페이지 전체 요청을 hero 하나로 보내는 규칙이 라우터 프롬프트에서 빠졌습니다.");
        assertTrue(router.contains("되돌리기·취소는 삭제(DELETE)가 아니다."),
                "되돌리기 요청이 DELETE 로 새는 걸 막는 문장이 빠졌습니다.");
        assertTrue(router.contains("겉모양(색 · 분위기 · 크기 · 굵기 · 배치 · 이미지)을 바꾸려는 말이 없는 단순 만들기·작성 요청에는 STYLE 을 쓰지 않는다."),
                "대상 없는 작성 요청이 STYLE 로 새는 걸 막는 문장이 빠졌습니다.");
        assertTrue(router.contains("\"원래 색으로 되돌려줘\""),
                "되돌리기 예시가 라우터 프롬프트에서 빠졌습니다.");
    }

    /**
     * #230 — 생성 프롬프트가 지어내기를 시키던 규칙과 예시 값.
     * 혜택 "2개 이상" · "받는 값을 모두 채운다" 는 금지 규칙과 정면으로 충돌했고(뒤엣것이 이긴다),
     * 예시의 3GB · 55,000원 → 38,500원 · 선착순 100명은 요청에 없는데도 그대로 베껴졌다.
     */
    @Test
    @DisplayName("생성 프롬프트 — 혜택을 지어내게 시키는 규칙과 구체적인 예시 값이 없다")
    void 생성_프롬프트가_지어내기를_시키지_않는다() {
        String gen = PromptBuilder.generate("영화 예매권을 증정해");

        assertFalse(gen.contains("2개 이상 나열"), "혜택 2개 이상을 요구하면 하나뿐인 요청에서 둘째를 지어낸다");
        assertFalse(gen.contains("받는 값을 모두 채운다"), "값 칸 채우기를 시키면 값이 없는 요청에서 수치를 만든다");
        for (String example : new String[]{"3GB", "55,000원", "38,500", "78% OFF", "선착순 100명", "30% 할인", "6개월 한정"}) {
            assertFalse(gen.contains(example), "프롬프트 예시에 구체적인 값이 남아 있습니다: " + example);
        }
        assertTrue(gen.contains("요청문에 없는 혜택 항목을 더하지 마라"), "혜택을 더하지 말라는 규칙이 빠졌습니다");
        assertTrue(gen.contains("요청문에 없는 숫자"), "요청에 없는 숫자 금지 규칙이 빠졌습니다");
        assertEquals(1, Block.BENEFITS.minItems(),
                "혜택 최소 개수를 2 로 올리면 검증기가 하나뿐인 혜택을 재시도시켜 결국 둘째를 지어낸다");
    }

    @Test
    @DisplayName("CSS 정화 — 허용 속성만 남는다")
    void CSS_정화가_위험한_속성을_지운다() {
        String dirty = """
                <section data-block="hero" style="color:red; position:fixed; z-index:9999; background-image:url(javascript:alert(1))">
                  <h1>제목</h1>
                </section>""";
        String clean = BlockValidator.sanitizeGenerated(dirty);

        assertTrue(clean.contains("color:red"),   "허용 속성 color 가 지워졌습니다: " + clean);
        assertFalse(clean.contains("position"),   "position 이 남았습니다. 화면을 덮을 수 있습니다: " + clean);
        assertFalse(clean.contains("z-index"),    "z-index 가 남았습니다: " + clean);
        assertFalse(clean.contains("javascript"), "javascript: 가 남았습니다: " + clean);
    }

    @Test
    @DisplayName("CSS 정화는 수정 쪽에도 똑같이 걸린다")
    void CSS_정화가_수정에도_걸린다() {
        String dirty = "<section data-block=\"hero\" style=\"color:red; position:fixed\"><h1>제목</h1></section>";
        String clean = BlockValidator.sanitizeEdited(dirty);

        assertTrue(clean.contains("color:red"), "허용 속성이 지워졌습니다: " + clean);
        assertFalse(clean.contains("position"),
                "수정 경로에서 position 이 통과했습니다. 정화는 양쪽에 같아야 합니다: " + clean);
    }

    @Test
    @DisplayName("슬롯 선택자는 레지스트리에서 나온다")
    void 슬롯_선택자() {
        assertEquals("[data-slot=\"period\"]",   Slot.PERIOD.selector());
        assertEquals("[data-slot=\"cta-link\"]", Slot.CTA_LINK.selector());
        assertThrows(IllegalArgumentException.class, () -> Slot.of("없는슬롯"));
    }


     private static final TemplateLoader TEMPLATES = new ResourceTemplateLoader();

     @Test
     @DisplayName("container 와 minItems 가 짝을 이룬다")
     void 컨테이너와_최소개수가_짝이다() {
         for (Block b : Block.values()) {
             if (b.minItems() > 0) {
                 assertNotNull(b.container(), b.key() + " 는 항목을 세는데 담는 자리가 없습니다.");
             } else {
                 assertNull(b.container(), b.key() + " 는 항목 개념이 없는데 container 가 있습니다.");
             }
         }
     }

     @Test
     @DisplayName("★ 템플릿 5종에서 container 가 항목을 정확히 담는다 — benefits · steps 둘 다")
     void 템플릿_5종에서_컨테이너가_항목을_담는다() {
         assertEquals(5, TEMPLATES.all().size());

         for (TemplateLoader.Source t : TEMPLATES.all()) {
             var body = Jsoup.parse(t.html()).body();

             for (Block b : List.of(Block.BENEFITS, Block.STEPS)) {
                 String where = t.code() + " / " + b.key();

                 var sections = body.select(b.selector());
                 assertEquals(1, sections.size(), where + " 섹션이 하나가 아닙니다.");

                 // ★ 자손으로 찾는다. template_5 는 .benefits-list 가 한 단계 더 안에 있다
                 var containers = sections.first().select(b.container());
                 assertEquals(1, containers.size(),
                         where + " 컨테이너가 하나가 아닙니다. 찾은 수=" + containers.size()
                         + " (선택자 " + b.container() + ")");

                 // ★ 개수는 섹션이 아니라 컨테이너에서 센다.
                 //   template_5 의 benefits 는 게이지·진행바가 컨테이너 밖에 있다
                 int items = containers.first().select(b.must()).size();
                 assertTrue(items >= b.minItems(),
                         where + " 항목이 minItems 보다 적습니다. " + items + " < " + b.minItems());

                 // 컨테이너 밖에서 세면 달라지는지 — template_5 가 이걸 증명한다
                 int fromSection = sections.first().select(b.must()).size();
                 assertEquals(items, fromSection,
                         where + " 컨테이너 밖에 같은 클래스의 항목이 있습니다. "
                         + "개수를 셀 때 반드시 container() 안에서 세야 합니다.");
             }
         }
     }
}
