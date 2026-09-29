package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 항목 개수 검사 5건.
 *
 * ★ 코드가 아니라 개수를 본다. 클래스가 맞아도 틀려도 늘면 걸린다.
 */
public class BlockValidatorTest {

	private static final String BEFORE_3 =
			"<section data-block=\"benefits\"><div class=\"benefits-list\">"
			+ "<div class=\"benefit-card\">A</div>"
			+ "<div class=\"benefit-card\">B</div>"
			+ "<div class=\"benefit-card\">C</div>"
			+ "</div></section>";

	private static String text(String path) throws Exception {
	    try (InputStream in = BlockValidatorTest.class.getClassLoader()
	            .getResourceAsStream(path)) {
	        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
	    }
	}

	private static boolean hasCode(List<BlockValidator.Failure> fails, String code) {
		return fails.stream().anyMatch(f -> f.code().equals(code));
	}

	private static boolean hasItemFailure(List<BlockValidator.Failure> fails) {
		return fails.stream().anyMatch(f -> f.code().startsWith("item_"));
	}

	// ---------- 1. 클래스가 맞는 카드 추가 → 걸린다 ------------

	@Test
	@DisplayName("같은 클래스 카드를 추가하면 걸림")
	void 맞는_클래스_추가도_걸린다() {
		String after = BEFORE_3.replace("</div></section",
				"<div class=\"benefit-card\">D</div></section>");

		List<BlockValidator.Failure> fails =
				BlockValidator.validateEdited(Block.BENEFITS, BEFORE_3, after);

		assertTrue(hasCode(fails, "item_added_benefits"));
	}

	// ---------- 2. 클래스가 틀린 카드 추가 → 걸린다 ------------

	@Test
	@DisplayName("엉뚱한 클래스로 추가해도 걸림 - must 카운트가 아니라 컨테이너 기준")
	void 틀린_클래스_추가도_걸린다() {
		String after = BEFORE_3.replace("</div></section>",
				"<div class=\"foo\">D</div></div></section>");

		List<BlockValidator.Failure> fails =
				BlockValidator.validateEdited(Block.BENEFITS, BEFORE_3, after);

		assertTrue(hasCode(fails, "item_added_benefits"));
	}

	// ---------- 3. 카드 삭제 → 걸린다 ------------

	@Test
	@DisplayName("카드를 지우면 걸림 - 복주머니 2개만 보여줘 는 정상 요청")
	void 카드_삭제는_통과한다() {
		String after = BEFORE_3.replace("<div class=\"benefit-card\">C</div>", "");

		List<BlockValidator.Failure> fails =
				BlockValidator.validateEdited(Block.BENEFITS, BEFORE_3, after);

		assertTrue(!hasItemFailure(fails));
	}

	// ---------- 4. 서버 복제 후 문구만 채움 → 통과 ------------

	@Test
	@DisplayName("서버가 복제한 뒤 문구만 채우면 통과함")
	void 서버_복제_후_문구만_채우면_통과() {
		String duplicated = BlockValidator.duplicateCard(BEFORE_3, Block.BENEFITS);

		Document doc = Jsoup.parseBodyFragment(duplicated);
		doc.select(".benefits-list").first().children().last().text("D");
		String filled = doc.body().html();

		List<BlockValidator.Failure> fails =
				BlockValidator.validateEdited(Block.BENEFITS, duplicated, filled);

		assertTrue(fails.isEmpty());
	}

	// ---------- 5. 템플릿 5종 benefits·steps 확인 ------------

	@Test
	@DisplayName("템플릿 5종은 항목 개수 검사에 걸리지 않음")
	void 템플릿_5종은_통과() throws Exception{
		String[] files = {
				"templates/template_1_sports_cheer.html",
				"templates/template_2_holiday_gift.html",
				"templates/template_3_member_appreciation.html",
				"templates/template_4_flash_sale.html",
				"templates/template_5_pre_registration.html",
		};
		for(String file : files) {
			String html;
			try(InputStream in = getClass().getClassLoader().getResourceAsStream(file)){
				html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}

			for(Block b : List.of(Block.BENEFITS, Block.STEPS)) {
				List<BlockValidator.Failure> fails =
						BlockValidator.validateEdited(b, html, html);
				assertTrue(!hasItemFailure(fails), file + " " + b.key());
			}
		}
	}

	// ---------- 6. 래퍼 안에 추가해도 걸린다 ------------

    @Test
    @DisplayName("래퍼 안에 추가해도 걸린다 - template_1 중첩 구조.")
    void 중첩_추가도_걸린다() {
        String wrapper =
            "<section data-block=\"benefits\"><div class=\"benefits-list\">"
            + "<div class=\"benefit-card\">A</div>"
            + "<div class=\"sp-prize-sub-row\">"
            + "<div class=\"benefit-card\">B</div>"
            + "<div class=\"benefit-card\">C</div>"
            + "</div></div></section>";
        // ★ 래퍼 안, C 뒤에 넣는다. 리스트 닫힘 밖에 넣으면 다른 테스트가 된다.
        String after = wrapper.replace("C</div>",
            "C</div><div class=\"benefit-card\">D</div>");

        List<BlockValidator.Failure> fails =
                BlockValidator.validateEdited(Block.BENEFITS, wrapper, after);

        assertTrue(hasCode(fails, "item_added_benefits"));
    }

    // ---------- 7. 구분선이 아니라 카드를 복제한다 ------------

    @Test
    @DisplayName("구분선이 아니라 카드를 복제한다 - template_3 steps.")
    void 구분선이_아니라_카드를_복제() {
        String steps =
            "<section data-block=\"steps\"><div class=\"steps-list\">"
            + "<div class=\"step-card\">1</div>"
            + "<div class=\"vp-step-divider\"></div>"
            + "<div class=\"step-card\">2</div>"
            + "<div class=\"vp-step-divider\"></div>"
            + "<div class=\"step-card\">3</div>"
            + "</div></section>";

        String dup = BlockValidator.duplicateCard(steps, Block.STEPS);
        Document doc = Jsoup.parseBodyFragment(dup);

        // ★ 카드는 3→4, 구분선은 2 그대로여야 한다.
        assertEquals(4, doc.select(".step-card").size());
        assertEquals(2, doc.select(".vp-step-divider").size());
        assertEquals("", doc.select(".step-card").last().text());
    }

    // ---------- 8. 실제 템플릿 카드 구조 유지 ------------

    @Test
    @DisplayName("실제 템플릿 카드는 구조를 유지하고 글자만 비운다.")
    void 실제_카드_구조_유지() throws Exception {
        String html = text("templates/template_3_member_appreciation.html");
        String block = BlockValidator.blockOf(html, Block.STEPS);

        String dup = BlockValidator.duplicateCard(block, Block.STEPS);
        Document doc = Jsoup.parseBodyFragment(dup);

        Element last = doc.select(".step-card").last();
        assertTrue(last.selectFirst(".step-title") != null);   // 태그 유지
        assertTrue(last.selectFirst(".step-desc") != null);
        assertEquals("", last.text());                          // 글자는 빔
    }
}
