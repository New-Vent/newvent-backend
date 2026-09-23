package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 슬롯이 수정에서 살아남는지 본다.
 *
 * ★ 이 파일이 막는 사고
 *   "제목만 바꿔줘" 한 번에 그 이벤트가 기간을 영원히 못 채우게 되는 것.
 *   merge 가 블록을 통째로 갈아끼우기 때문에, 슬롯이 한 번 사라지면 복구할 자리가 없다.
 */
class SlotPreservationTest {

    /** 템플릿이 주는 hero — 기간 자리가 비어 있다 */
    private static final String HERO_BEFORE = """
            <section data-block="hero" class="vp-hero">\
            <h1>VIP 단독 혜택</h1>\
            <p class="vp-period" data-slot="period"></p>\
            </section>""";

    // ── 정화: 생성과 수정이 반대로 동작한다 ──────────────────────

    @Test
    @DisplayName("생성 정화는 data-slot 을 지운다")
    void 생성은_슬롯을_지운다() {
        String dirty = "<section data-block=\"hero\"><span data-slot=\"period\">가짜 기간</span></section>";

        String clean = BlockValidator.sanitizeGenerated(dirty);

        assertFalse(clean.contains("data-slot"),
                "생성에서 data-slot 이 남았습니다. 모델이 서버의 쓰기 지점을 만들 수 있습니다: " + clean);
    }

    @Test
    @DisplayName("수정 정화는 data-slot 을 남긴다")
    void 수정은_슬롯을_남긴다() {
        String clean = BlockValidator.sanitizeEdited(HERO_BEFORE);

        assertTrue(clean.contains("data-slot=\"period\""),
                "수정에서 data-slot 이 지워졌습니다. 이 블록은 이제 기간을 못 채웁니다: " + clean);
    }

    @Test
    @DisplayName("수정 정화도 script 는 지운다")
    void 수정도_스크립트는_지운다() {
        String dirty = HERO_BEFORE.replace("</section>", "<script>alert(1)</script></section>");

        String clean = BlockValidator.sanitizeEdited(dirty);

        assertFalse(clean.contains("<script"), "script 가 남았습니다: " + clean);
        assertTrue(clean.contains("data-slot"), "슬롯까지 같이 날아갔습니다: " + clean);
    }

    // ── 검증: 원본 대비로 판정한다 ────────────────────────────────

    @Test
    @DisplayName("슬롯을 그대로 두고 제목만 바꾸면 통과")
    void 정상_수정은_통과한다() {
        String after = HERO_BEFORE.replace("VIP 단독 혜택", "VIP 고객님만을 위한 단독 혜택");

        List<BlockValidator.Failure> fails =
                BlockValidator.validateEdited(Block.HERO, HERO_BEFORE, after);

        assertTrue(fails.isEmpty(), "정상 수정이 걸렸습니다: " + fails);
    }

    @Test
    @DisplayName("★ 슬롯을 지우면 잡힌다")
    void 슬롯을_지우면_실패한다() {
        String after = """
                <section data-block="hero" class="vp-hero">\
                <h1>VIP 단독 혜택</h1>\
                </section>""";

        List<BlockValidator.Failure> fails =
                BlockValidator.validateEdited(Block.HERO, HERO_BEFORE, after);

        assertTrue(fails.stream().anyMatch(f -> f.code().equals("slot_lost_period")),
                "슬롯이 사라졌는데 통과했습니다. 이 이벤트는 기간을 영영 못 채웁니다: " + fails);
    }

    @Test
    @DisplayName("★ 슬롯을 지어내면 잡힌다")
    void 슬롯을_만들면_실패한다() {
        String after = HERO_BEFORE.replace("</section>",
                "<a data-slot=\"cta-link\" href=\"#\">참여</a></section>");

        List<BlockValidator.Failure> fails =
                BlockValidator.validateEdited(Block.HERO, HERO_BEFORE, after);

        assertTrue(fails.stream().anyMatch(f -> f.code().equals("slot_invented_cta-link")),
                "없던 슬롯을 만들었는데 통과했습니다: " + fails);
    }

    @Test
    @DisplayName("실패 메시지가 고칠 수 있는 말이어야 한다")
    void 되먹임이_의미_메시지다() {
        String after = "<section data-block=\"hero\"><h1>제목</h1></section>";

        List<BlockValidator.Failure> fails =
                BlockValidator.validateEdited(Block.HERO, HERO_BEFORE, after);

        String msg = fails.stream()
                .filter(f -> f.code().startsWith("slot_lost"))
                .findFirst().orElseThrow().message();

        assertTrue(msg.contains("data-slot"), "무엇을 고쳐야 하는지 안 나옵니다: " + msg);
        assertTrue(msg.contains("period"), "어느 슬롯인지 안 나옵니다: " + msg);
    }

    // ── 프롬프트 ──────────────────────────────────────────────────

    @Test
    @DisplayName("수정 프롬프트는 슬롯을 지키라고 말한다")
    void 수정_프롬프트에_슬롯_규칙이_있다() {
        String system = PromptBuilder.edit(Block.HERO);

        assertTrue(system.contains("data-slot"),
                "수정 프롬프트에 슬롯 규칙이 없습니다. 검증으로만 막으면 재시도를 낭비합니다.");
    }

    @Test
    @DisplayName("생성 프롬프트는 슬롯을 언급하지 않는다")
    void 생성_프롬프트에는_슬롯이_없다() {
        String system = PromptBuilder.generate();

        assertFalse(system.contains("data-slot"),
                "생성 프롬프트가 슬롯을 알려주고 있습니다. 모르는 게 안전합니다.");
    }

    // ── 도우미 ────────────────────────────────────────────────────

    @Test
    @DisplayName("blockOf 가 수정 전 블록을 떼어낸다")
    void 블록만_떼어낸다() {
        String doc = HERO_BEFORE + "<section data-block=\"cta\"><a href=\"#\">참여</a></section>";

        String hero = BlockValidator.blockOf(doc, Block.HERO);

        assertTrue(hero.contains("data-slot=\"period\""), "슬롯이 빠졌습니다: " + hero);
        assertFalse(hero.contains("data-block=\"cta\""), "다른 블록까지 왔습니다: " + hero);
        assertEquals("", BlockValidator.blockOf(doc, Block.BENEFITS), "없는 블록은 빈 문자열이어야 합니다.");
    }
}
