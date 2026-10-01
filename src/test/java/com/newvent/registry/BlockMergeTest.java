package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.generation.service.ResourceTemplateLoader;
import com.newvent.generation.service.TemplateLoader;

class BlockMergeTest {

    private static final TemplateLoader TEMPLATES = new ResourceTemplateLoader();

    @Test
    @DisplayName("★ 템플릿 5종 × 전 블록의 추출·병합은 원본과 바이트 단위로 같다")
    void 모든_템플릿_모든_블록_왕복() {
        assertEquals(5, TEMPLATES.all().size());
        for (TemplateLoader.Source template : TEMPLATES.all()) {
            String html = template.html();
            for (Block block : Block.values()) {
                if (!block.core()) continue;    // 템플릿 5종은 core 블록만 갖는다 (highlight · faq 등은 없다)
                String extracted = BlockMerge.extract(html, block);
                String merged = BlockMerge.merge(html, block, extracted);
                String where = template.code() + " / " + block.key();

                assertTrue(extracted.startsWith("<section"), where + " 시작 태그가 빠졌습니다.");
                assertTrue(extracted.endsWith("</section>"), where + " 종료 태그가 빠졌습니다.");
                assertArrayEquals(html.getBytes(StandardCharsets.UTF_8),
                        merged.getBytes(StandardCharsets.UTF_8), where + " 왕복 결과가 달라졌습니다.");
            }
        }
    }

    @Test
    @DisplayName("없는 data-block 과 중복 data-block 은 예외다")
    void 없거나_중복인_블록은_거부() {
        String missing = "<section class=\"block-hero\">제목</section>";
        String duplicate = "<section data-block=\"hero\">하나</section>"
                + "<section data-block=\"hero\">둘</section>";

        assertThrows(IllegalArgumentException.class, () -> BlockMerge.extract(missing, Block.HERO));
        assertThrows(IllegalArgumentException.class,
                () -> BlockMerge.merge(missing, Block.HERO,
                        "<section data-block=\"hero\">새 제목</section>"));
        assertThrows(IllegalArgumentException.class, () -> BlockMerge.extract(duplicate, Block.HERO));
        assertThrows(IllegalArgumentException.class,
                () -> BlockMerge.merge(duplicate, Block.HERO,
                        "<section data-block=\"hero\">새 제목</section>"));
    }

    @Test
    @DisplayName("교체 섹션도 대상 블록 하나만 허용한다")
    void 잘못된_교체_섹션은_거부() {
        String html = TEMPLATES.all().get(0).html();

        assertThrows(IllegalArgumentException.class,
                () -> BlockMerge.merge(html, Block.HERO,
                        "<section data-block=\"cta\">다른 블록</section>"));
        assertThrows(IllegalArgumentException.class,
                () -> BlockMerge.merge(html, Block.HERO,
                        "<section data-block=\"hero\">하나</section>"
                        + "<section data-block=\"hero\">둘</section>"));
        assertThrows(IllegalArgumentException.class,
                () -> BlockMerge.merge(html, Block.HERO,
                        "<section data-block=\"hero\">제목</section><p>남는 HTML</p>"));
    }

    @Test
    @DisplayName("섹션을 교체해도 다른 블록의 class·id·data-slot 과 바깥 스크립트가 그대로다")
    void 대상_밖은_원본_그대로() {
        String html = TEMPLATES.find("holiday_gift").orElseThrow().html();
        String oldHero = BlockMerge.extract(html, Block.HERO);
        String newHero = oldHero.replaceFirst("<h1", "<h1 data-edit=\"new\"");
        assertNotEquals(oldHero, newHero, "테스트에서 섹션을 수정하지 못했습니다.");

        String merged = BlockMerge.merge(html, Block.HERO, newHero);
        int start = html.indexOf(oldHero);
        assertTrue(start >= 0);
        assertEquals(html.substring(0, start), merged.substring(0, start));
        assertEquals(html.substring(start + oldHero.length()),
                merged.substring(start + newHero.length()));
        assertEquals(newHero, BlockMerge.extract(merged, Block.HERO));

        for (Block block : Block.values()) {
            if (block != Block.HERO && block.core()) {
                assertEquals(BlockMerge.extract(html, block), BlockMerge.extract(merged, block),
                        block.key() + " 의 class·id·data-slot 이 바뀌었습니다.");
            }
        }
    }

    @Test
    @DisplayName("주석과 스크립트의 가짜 태그는 블록으로 세지 않고 CRLF도 보존한다")
    void 주석과_스크립트는_무시하고_줄바꿈_보존() {
        String html = "<!-- <section data-block='hero'>가짜</section> -->\r\n"
                + "<section data-block='hero' class='a'><h1>실제</h1></section>\r\n"
                + "<script>const s = \"<section data-block='hero'>가짜</section>\";</script>";
        String section = BlockMerge.extract(html, Block.HERO);

        assertEquals("<section data-block='hero' class='a'><h1>실제</h1></section>", section);
        assertEquals(html, BlockMerge.merge(html, Block.HERO, section));
    }

    @Test
    @DisplayName("section 이 아닌 태그도 찾는다 — 검증기와 같은 선택자를 써야 한다")
    void 섹션이_아닌_태그도_찾는다() {
        // ★ validateEdited 는 target.selector() 만 본다. 여기서 "section" 을 덧붙이면
        //   검증은 통과하고 병합에서 터지는 구멍이 생긴다
        String html = "<div data-block=\"hero\"><h1>여름 이벤트</h1></div>\n"
                + "<section data-block=\"cta\"><button>참여하기</button></section>";

        assertEquals("<div data-block=\"hero\"><h1>여름 이벤트</h1></div>",
                BlockMerge.extract(html, Block.HERO));

        String merged = BlockMerge.merge(html, Block.HERO,
                "<div data-block=\"hero\"><h1>가을 이벤트</h1></div>");
        assertEquals("<div data-block=\"hero\"><h1>가을 이벤트</h1></div>\n"
                + "<section data-block=\"cta\"><button>참여하기</button></section>", merged);
    }
}
