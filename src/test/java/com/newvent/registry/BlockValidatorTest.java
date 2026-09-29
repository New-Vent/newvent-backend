package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
	@DisplayName("카드를 지우면 걸림")
	void 카드_삭제는_걸린다() {
		String after = BEFORE_3.replace("<div class=\"benefit-card\">C</div>", "");

		List<BlockValidator.Failure> fails =
				BlockValidator.validateEdited(Block.BENEFITS, BEFORE_3, after);

		assertTrue(hasCode(fails, "item_removed_benefits"));
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
}
