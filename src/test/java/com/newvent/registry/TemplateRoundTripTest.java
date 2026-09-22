package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.generation.service.ResourceTemplateLoader;
import com.newvent.generation.service.TemplateLoader;
import com.newvent.generation.service.TemplateService;

/**
 * 템플릿 5종을 정화기·검증기에 통과시켜 **무엇이 없어지는지 센다.**
 *
 * ★ 왜 이 테스트인가
 *   href="#" 유실을 6주 동안 못 봤다. 스모크 테스트가 **우연히** 잡았다.
 *   Jsoup Safelist 가 무엇을 지우는지 하나씩 따지는 건 끝이 없다.
 *   실제 템플릿을 통과시켜 개수를 세는 게 확실하고, 템플릿이 바뀌어도 자동으로 다시 본다.
 *
 * ★ DB 를 안 띄운다
 *   ResourceTemplateLoader 가 classpath 에서 읽는다. 순수 JUnit 이라 밀리초에 돈다.
 *   정본이 DB 로 옮겨가도 거기 들어가는 건 이 파일들이므로 검사 대상은 그대로다.
 */
class TemplateRoundTripTest {

    private static final TemplateLoader LOADER = new ResourceTemplateLoader();

    private static int count(String html, String selector) {
        return Jsoup.parseBodyFragment(html).body().select(selector).size();
    }

    private static String attrDump(String html) {
        Document d = Jsoup.parseBodyFragment(html);
        return "[slot=" + Slots.keysOf(html) + " id=" + Slots.idsOf(html)
                + " button=" + d.body().select("button").size()
                + " input=" + d.body().select("input").size() + "]";
    }

    // ── 로더 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("템플릿 5종이 전부 읽힌다")
    void 다섯_개가_있다() {
        List<TemplateLoader.Source> all = LOADER.all();

        assertEquals(5, all.size(), "templates.json 과 html 파일 수가 맞는지 보세요: " + all.size());
        for (TemplateLoader.Source t : all) {
            assertFalse(t.html().isBlank(), t.code() + " 의 html 이 비었습니다.");
            assertTrue(t.builtin(), t.code() + " 가 builtin 이 아닙니다. 기본 5종은 전부 builtin 입니다.");
            assertFalse(t.description().isBlank(),
                    t.code() + " 에 description 이 없습니다. RAG 템플릿 추천의 임베딩 대상입니다.");
        }
    }

    @Test
    @DisplayName("템플릿마다 슬롯이 정확히 2개다")
    void 슬롯이_둘씩_있다() {
        for (TemplateLoader.Source t : LOADER.all()) {
            assertEquals(java.util.Set.of("period", "cta-link"), Slots.keysOf(t.html()),
                    t.code() + " 의 슬롯이 다릅니다. 템플릿 담당과 맞춰야 합니다.");
        }
    }

    // ── ① 검증기가 템플릿을 받아주는가 ──────────────────────────────

    @Test
    @DisplayName("★ 템플릿 5종 × 모든 블록이 검증을 통과한다")
    void 템플릿이_검증을_통과한다() {
        for (TemplateLoader.Source t : LOADER.all()) {
            for (Block b : Block.llmBlocks()) {
                String block = BlockValidator.blockOf(t.html(), b);
                assertFalse(block.isBlank(), t.code() + " 에 " + b.key() + " 블록이 없습니다.");

                // 원본을 그대로 돌려준 셈 — 아무것도 안 고쳤으니 반드시 통과해야 한다
                List<BlockValidator.Failure> fails = BlockValidator.validateEdited(b, block, block);
                assertTrue(fails.isEmpty(),
                        t.code() + " / " + b.key() + " 가 검증에 걸립니다: " + fails
                        + "\nBlock.must() 가 템플릿 마크업을 못 받고 있습니다.");
            }
        }
    }

    // ── ② 정화가 무엇을 지우는가 ────────────────────────────────────

    @Test
    @DisplayName("★ 수정 정화를 거쳐도 유실이 없다")
    void 정화가_아무것도_안_지운다() {
        for (TemplateLoader.Source t : LOADER.all()) {
            for (Block b : Block.llmBlocks()) {
                String before = BlockValidator.blockOf(t.html(), b);
                String after  = BlockValidator.sanitizeEdited(before);

                String where = t.code() + " / " + b.key();
                assertEquals(Slots.keysOf(before), Slots.keysOf(after), where + " data-slot 유실");
                assertEquals(Slots.idsOf(before),  Slots.idsOf(after),  where + " id 유실");

                assertEquals(Slots.classMap(before), Slots.classMap(after),
                        where + " class 유실 — ev-block 이 떨어지면 event.css 의 "
                        + ":not(.ev-block) 폴백이 템플릿에 걸립니다");

                for (String sel : List.of("button", "input", "a[href]", "img[src]",
                                          "[data-demo-msg]", "[data-state]", "[data-vote]")) {
                    assertEquals(count(before, sel), count(after, sel),
                            where + " — " + sel + " 이 " + attrDump(before)
                            + " → " + attrDump(after) + " 로 줄었습니다");
                }
            }
        }
    }

    @Test
    @DisplayName("생성 정화는 반대로 — 버튼도 슬롯도 지운다")
    void 생성_정화는_지운다() {
        String cta = BlockValidator.blockOf(LOADER.all().get(0).html(), Block.CTA);

        String cleaned = BlockValidator.sanitizeGenerated(cta);

        assertEquals(0, count(cleaned, "button"),
                "생성 정화가 button 을 남겼습니다. 동작할 스크립트가 없어 죽은 버튼이 됩니다.");
        assertTrue(Slots.keysOf(cleaned).isEmpty(),
                "생성 정화가 data-slot 을 남겼습니다. 모델이 서버의 쓰기 지점을 만들 수 있습니다.");
    }

    // ── ③ 지어낸 것은 걸리는가 ──────────────────────────────────────

    @Test
    @DisplayName("★ 카드를 지우거나 늘려도 통과한다 — 개수는 자유")
    void 카드_추가_삭제는_통과한다() {
        String benefits = BlockValidator.blockOf(
                LOADER.find("template_2_holiday_gift").orElseThrow().html(), Block.BENEFITS);

        Document d = Jsoup.parseBodyFragment(benefits);
        d.body().select(".benefit-card").last().remove();     // 복주머니 하나 지우기
        String after = BlockValidator.sanitizeEdited(d.body().html());

        List<BlockValidator.Failure> fails =
                BlockValidator.validateEdited(Block.BENEFITS, benefits, after);
        assertTrue(fails.isEmpty(),
                "카드를 지웠더니 걸렸습니다: " + fails
                + "\n개수를 대조하면 \"복주머니를 2개만 보여줘\" 가 영원히 실패합니다.");
    }

    @Test
    @DisplayName("id 를 지우면 걸린다")
    void id를_지우면_걸린다() {
        String hero = BlockValidator.blockOf(
                LOADER.find("template_4_flash_sale").orElseThrow().html(), Block.HERO);
        assertFalse(Slots.idsOf(hero).isEmpty(), "이 블록에 id 가 없습니다. 테스트 대상을 바꾸세요.");

        Document d = Jsoup.parseBodyFragment(hero);
        d.body().select("[id]").forEach(e -> e.removeAttr("id"));

        List<BlockValidator.Failure> fails =
                BlockValidator.validateEdited(Block.HERO, hero, d.body().html());
        assertTrue(fails.stream().anyMatch(f -> f.code().startsWith("id_lost_")),
                "id 가 사라졌는데 통과했습니다. 타이머가 멈춥니다: " + fails);
    }

    @Test
    @DisplayName("없던 id 를 만들면 걸린다")
    void id를_만들면_걸린다() {
        String cta = BlockValidator.blockOf(LOADER.all().get(0).html(), Block.CTA);

        Document d = Jsoup.parseBodyFragment(cta);
        d.body().select("button").first().attr("id", "내가만든버튼");

        List<BlockValidator.Failure> fails =
                BlockValidator.validateEdited(Block.CTA, cta, d.body().html());
        assertTrue(fails.stream().anyMatch(f -> f.code().equals("id_invented_내가만든버튼")),
                "없던 id 를 만들었는데 통과했습니다: " + fails);
    }

    // ── ③-2 class 보존 ────────────────────────────────────────────

    @Test
    @DisplayName("★ ev-block 을 떼면 걸린다 — 제일 조용한 고장")
    void ev_block을_떼면_걸린다() {
        String hero = BlockValidator.blockOf(
                LOADER.find("template_1_sports_cheer").orElseThrow().html(), Block.HERO);
        assertTrue(hero.contains("ev-block"), "이 템플릿에 ev-block 이 없습니다. 대상을 바꾸세요.");

        Document d = Jsoup.parseBodyFragment(hero);
        d.body().selectFirst("section[data-block]").removeClass("ev-block");

        List<BlockValidator.Failure> fails =
                BlockValidator.validateEdited(Block.HERO, hero, d.body().html());
        assertTrue(fails.stream().anyMatch(f -> f.code().equals("class_changed_root")),
                "ev-block 이 떨어졌는데 통과했습니다. "
                + "event.css 의 :not(.ev-block) 폴백이 템플릿에 걸려 디자인이 바뀝니다: " + fails);
    }

    @Test
    @DisplayName("★ 슬롯 요소의 class 를 지우면 걸린다 — 버튼이 안 눌린다")
    void 슬롯의_class를_지우면_걸린다() {
        String cta = BlockValidator.blockOf(
                LOADER.find("template_1_sports_cheer").orElseThrow().html(), Block.CTA);

        Document d = Jsoup.parseBodyFragment(cta);
        var slot = d.body().selectFirst(Slot.CTA_LINK.selector());
        assertNotNull(slot, "cta-link 슬롯이 없습니다. 대상을 바꾸세요.");
        assertFalse(slot.className().isBlank(), "이 슬롯에 class 가 없습니다. 대상을 바꾸세요.");
        slot.removeAttr("class");   // cta-btn / btn 이 날아간다

        List<BlockValidator.Failure> fails =
                BlockValidator.validateEdited(Block.CTA, cta, d.body().html());
        assertTrue(fails.stream().anyMatch(f -> f.code().equals("class_changed_slot:cta-link")),
                "CTA 버튼의 class 가 사라졌는데 통과했습니다. "
                + "스크립트가 closest('.cta-btn, .btn') 으로 잡으므로 버튼이 죽습니다: " + fails);
    }

    @Test
    @DisplayName("class 순서만 바꾸는 건 통과한다")
    void class_순서는_상관없다() {
        String hero = BlockValidator.blockOf(
                LOADER.find("template_3_member_appreciation").orElseThrow().html(), Block.HERO);

        Document d = Jsoup.parseBodyFragment(hero);
        var root = d.body().selectFirst("section[data-block]");
        List<String> reversed = new java.util.ArrayList<>(root.classNames());
        java.util.Collections.reverse(reversed);
        root.attr("class", String.join("  ", reversed));   // 순서 뒤집기 + 공백 두 칸

        List<BlockValidator.Failure> fails =
                BlockValidator.validateEdited(Block.HERO, hero, d.body().html());
        assertTrue(fails.isEmpty(),
                "순서만 바꿨는데 걸렸습니다. 재시도 4번이 전부 같은 이유로 터집니다: " + fails);
    }

    // ── ④ 초기 HTML — 슬롯 비우기 ───────────────────────────────────

    @Test
    @DisplayName("★ 초기 HTML — period 는 비고 CTA 문구는 남는다")
    void 슬롯을_비워도_버튼_문구는_남는다() {
        TemplateService service = new TemplateService(LOADER);

        for (TemplateLoader.Source t : LOADER.all()) {
            String html = service.initialHtml(t.code());
            Document d = Jsoup.parseBodyFragment(html);

            assertEquals("", d.body().select(Slot.PERIOD.selector()).text(),
                    t.code() + " 의 기간이 안 비워졌습니다. 그 날짜가 굳어서 폼을 고쳐도 안 바뀝니다.");

            // ★ cta-link 는 내용이 아니라 링크 속성을 받는 자리다.
            //   empty() 로 비우면 버튼 문구가 날아간다.
            var cta = d.body().selectFirst(Slot.CTA_LINK.selector());
            assertNotNull(cta, t.code() + " 의 cta-link 슬롯이 사라졌습니다.");
            assertFalse(cta.text().isBlank(),
                    t.code() + " 의 CTA 문구가 지워졌습니다. cta-link 는 속성만 비워야 합니다.");
            assertFalse(cta.hasAttr("href"),  t.code() + " 에 href 가 남았습니다.");
            assertFalse(cta.hasAttr("data-href"), t.code() + " 에 data-href 가 남았습니다.");

            assertEquals(Slots.keysOf(t.html()), Slots.keysOf(html),
                    t.code() + " 슬롯 자체가 사라졌습니다. 비우는 것과 지우는 것은 다릅니다.");
        }
    }

    @Test
    @DisplayName("채우면 다시 값이 들어간다")
    void 채우기가_동작한다() {
        String html = new TemplateService(LOADER).initialHtml("template_3_member_appreciation");

        String filled = Slots.fill(html, "2026.10.01 ~ 2026.10.31", "https://event.example.com/vip");
        Document d = Jsoup.parseBodyFragment(filled);

        assertEquals("2026.10.01 ~ 2026.10.31",
                d.body().select(Slot.PERIOD.selector()).text());

        var cta = d.body().selectFirst(Slot.CTA_LINK.selector());
        // 템플릿 5종은 전부 <button> 이라 data-href 로 들어간다
        assertEquals("https://event.example.com/vip", cta.attr("data-href"));
        assertFalse(cta.text().isBlank(), "채우면서 버튼 문구가 날아갔습니다.");
    }
}
