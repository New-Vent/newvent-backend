package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.generation.service.HtmlPolicy;

/**
 * 선택 블록 열쇠말(Block.allowedFor)과, 입력에 원래 있던 대괄호를 자리표시자로 보지 않는 규칙.
 * 둘 다 Bedrock 실측에서 나온 문제다 — 지어낸 FAQ, "[단독]" 제목의 첫 시도 실패.
 */
class TriggerAndPlaceholderTest {

    private static final String CORE =
            "<section data-block=\"hero\"><h1>t</h1></section>"
            + "<section data-block=\"benefits\"><ul><li>a</li><li>b</li></ul></section>"
            + "<section data-block=\"cta\"><a href=\"#\" class=\"btn\">go</a></section>";
    private static final String FAQ =
            "<section data-block=\"faq\"><dl><dt>q</dt><dd>a</dd><dt>q</dt><dd>a</dd></dl></section>";
    private static final String COMPARE =
            "<section data-block=\"compare\"><table><tr><th>a</th></tr><tr><td>b</td></tr></table></section>";

    private static List<String> blocksOf(String html) {
        return Jsoup.parseBodyFragment(html).body().select("section[data-block]").eachAttr("data-block");
    }

    // ── 열쇠말 ──

    @Test
    @DisplayName("열쇠말이 없으면 faq · compare 는 안 되고, 있으면 된다. 열쇠말 없는 블록은 늘 된다")
    void 열쇠말_판정() {
        String plain = "신규 가입자에게 편의점 쿠폰을 드립니다.";
        assertFalse(Block.FAQ.allowedFor(plain));
        assertFalse(Block.COMPARE.allowedFor(plain));
        assertTrue(Block.HIGHLIGHT.allowedFor(plain));
        assertTrue(Block.STEPS.allowedFor(plain));

        assertTrue(Block.FAQ.allowedFor("자주 묻는 질문도 넣어 주세요"));
        assertTrue(Block.COMPARE.allowedFor("베이직·프리미엄 요금제 비교표"));
        assertTrue(Block.FAQ.allowedFor(null), "요청문을 모르면 거르지 않는다");
    }

    @Test
    @DisplayName("생성 프롬프트에서 열쇠말 없는 블록은 빠지고, 만들지 말라고 이름을 댄다")
    void 프롬프트에서_뺀다() {
        String p = PromptBuilder.generate("여름 쿠폰 이벤트입니다.");
        assertFalse(p.contains("data-block=\"faq\" :"), "faq 가 목록에 남았습니다");
        assertFalse(p.contains("v-faq-accordion"), "faq 변형이 남았습니다");
        assertTrue(p.contains("faq"), "만들지 말라는 안내가 없습니다");
        assertTrue(p.contains("영역을 만들지 마라"));

        String withFaq = PromptBuilder.generate("FAQ 도 넣어 주세요");
        assertTrue(withFaq.contains("data-block=\"faq\" :"));
    }

    @Test
    @DisplayName("모델이 그래도 만들면 정화 단계에서 지운다")
    void 출력에서_지운다() {
        HtmlPolicy policy = HtmlPolicy.generation("여름 쿠폰 이벤트입니다.", "여름 쿠폰");
        String cleaned = policy.clean(CORE + FAQ + COMPARE);

        assertEquals(List.of("hero", "benefits", "cta"), blocksOf(cleaned));
        assertTrue(policy.validate(cleaned).isEmpty(), "지운 뒤에도 검증을 통과해야 합니다");
    }

    @Test
    @DisplayName("요청문이 원하면 남긴다")
    void 요청하면_남긴다() {
        HtmlPolicy policy = HtmlPolicy.generation("자주 묻는 질문과 요금제 비교표를 넣어 주세요", "가을");
        assertEquals(List.of("hero", "benefits", "cta", "faq", "compare"), blocksOf(policy.clean(CORE + FAQ + COMPARE)));
    }

    @Test
    @DisplayName("같은 블록을 두 번 내면 extra_ 로 걸린다 — 이후 병합이 터지는 상태를 저장하지 않는다")
    void 중복_블록() {
        String twice = CORE + "<section data-block=\"highlight\"><p>a</p></section>"
                + "<section data-block=\"highlight\" class=\"v-highlight-alert\"><p>b</p></section>";
        assertTrue(BlockValidator.validateGenerated(twice).stream()
                .anyMatch(f -> f.code().equals("extra_highlight")), BlockValidator.validateGenerated(twice).toString());
    }

    // ── 대괄호 ──

    @Test
    @DisplayName("제목에 있던 대괄호는 자리표시자가 아니다. 새로 생긴 대괄호는 여전히 자리표시자다")
    void 제목_대괄호() {
        String withTitle = CORE.replace("<h1>t</h1>", "<h1>[단독] 가을 이벤트</h1>");
        String invented = CORE.replace("<h1>t</h1>", "<h1>[이벤트명] 가을</h1>");

        HtmlPolicy policy = HtmlPolicy.generation("가을 데이터 이벤트", "[단독] 가을 이벤트");
        assertTrue(policy.validate(withTitle).isEmpty(), "제목의 [단독] 이 걸렸습니다");
        assertTrue(policy.validate(invented).stream().anyMatch(f -> f.kind() == FailureCode.PLACEHOLDER),
                "지어낸 [이벤트명] 을 놓쳤습니다");

        assertTrue(BlockValidator.validateGenerated(withTitle).stream()
                .anyMatch(f -> f.kind() == FailureCode.PLACEHOLDER), "출처를 모르면 전부 자리표시자로 봐야 합니다");
    }

    @Test
    @DisplayName("허용된 대괄호와 지어낸 대괄호가 같이 있으면 걸린다")
    void 섞이면_걸린다() {
        String html = CORE.replace("<h1>t</h1>", "<h1>[단독] 가을</h1><p>[혜택 1]</p>");
        assertTrue(HtmlPolicy.generation("가을", "[단독] 가을").validate(html).stream()
                .anyMatch(f -> f.kind() == FailureCode.PLACEHOLDER));
    }
}
