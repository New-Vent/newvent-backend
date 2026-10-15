package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 팔레트에 없는 색 이름을 걸러내는 사전.
 *
 * ★ 이 테스트가 현재 팔레트 구성(노랑 · 하양 · 청록 · 살구 없음)을 못 박는다.
 *   팔레트에 그 색을 추가하면 ({@link Palette} 설명에 색 이름을 적으면) 아래 "지원 안 함" 테스트가 깨지는데,
 *   그건 안내가 사라지는 것이 맞다는 신호이므로 테스트에서 그 색을 지운다.
 */
class ColorWordsTest {

    private static String unsupportedName(String text) {
        return ColorWords.firstUnsupported(text).map(u -> u.group().name()).orElse(null);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "노란색으로 바꿔줘", "바탕색은 노란색으로 바꿔줘", "노랑으로 바꿔줘", "노란 느낌으로 해줘",
            "전체를 노랗게 해줘", "옐로우 계열로 해줘", "전체 색감을 노랑색으로",
    })
    @DisplayName("노랑 계열은 팔레트에 없다")
    void 노랑은_지원하지_않는다(String text) {
        assertEquals("노랑", unsupportedName(text));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "하얀색으로 바꿔줘", "흰색으로 바꿔줘", "전체를 하양으로", "배경색을 하얗게", "화이트 느낌으로 해줘", "흰 배경으로 해줘",
    })
    @DisplayName("하양 계열은 팔레트에 없다")
    void 하양은_지원하지_않는다(String text) {
        assertEquals("하양", unsupportedName(text));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"청록색으로 바꿔줘", "살구색 느낌으로 해줘"})
    @DisplayName("청록 · 살구도 팔레트에 없다")
    void 그_밖의_없는_색(String text) {
        assertTrue(ColorWords.firstUnsupported(text).isPresent(), text);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "파란색으로 바꿔줘", "파랑 계열로 해줘", "분홍색으로 해줘", "연보라로 바꿔줘", "보라색으로 바꿔줘",
            "검정색으로 해줘", "금색 느낌으로", "빨갛게 해줘", "초록색으로 해줘", "주황색으로", "갈색 톤으로", "회색으로",
            "하늘색으로 바꿔줘", "민트색으로", "남색으로", "은색으로",
    })
    @DisplayName("팔레트 설명에 있는 색은 걸러내지 않는다")
    void 있는_색은_통과한다(String text) {
        assertEquals(Optional.empty(), ColorWords.firstUnsupported(text), text);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "원래 색으로 되돌려줘", "원래대로 되돌려줘", "전체적으로 봄 느낌으로 바꿔줘", "차분한 분위기로 해줘",
            "블루투스 안내를 넣어줘", "그린다는 표현을 빼줘", "제목을 더 짧게 바꿔줘", "",
    })
    @DisplayName("색 말이 없거나 다른 낱말의 일부면 걸러내지 않는다")
    void 색_말이_없으면_통과한다(String text) {
        assertEquals(Optional.empty(), ColorWords.firstUnsupported(text), text);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "노란색 말고 파란색으로 바꿔줘", "노란색 빼고 파랑으로 해줘", "노란색 대신 파란색으로 바꿔줘", "노란색은 싫으니까 파랑으로",
            "노란색이 아니라 분홍색으로 바꿔줘", "노란색 쓰지 말고 파란색으로", "노랑 계열 말고 초록으로", "노란색 제외하고 파란색으로",
            "하얀색 아닌 파란색으로", "하얀색 말고 검정으로", "청록색 대신 파란색으로", "노랗게 말고 파랗게 해줘",
    })
    @DisplayName("피하려고 말한 색은 지원하지 않는 색이어도 거절하지 않는다 — '노란색 말고 파란색으로'")
    void 제외하는_색은_세지_않는다(String text) {
        assertEquals(Optional.empty(), ColorWords.firstUnsupported(text), text);
    }

    @Test
    @DisplayName("제외 표현이 있어도 다른 없는 색을 원하면 그 색을 짚는다")
    void 제외하는_말이_있어도_원하는_없는_색은_짚는다() {
        assertEquals("노랑", unsupportedName("파랑 말고 노랑으로 바꿔줘"));
        assertEquals("하양", unsupportedName("노란색 말고 하얀색으로 바꿔줘"));
        assertEquals("노랑", unsupportedName("노란색 말고 노란색으로"), "앞의 것만 제외다. 뒤의 노랑은 원하는 색");
    }

    @Test
    @DisplayName("영역 배경으로 말한 색은 페이지 전체 색으로 세지 않는다 — 같은 절에 그 영역을 가리키는 말이 있을 때")
    void 영역_배경_색은_세지_않는다() {
        Set<Block> benefits = Set.of(Block.BENEFITS);

        // 제목(hero)은 파랑, 혜택 배경은 노랑 — 노랑은 혜택 영역 배경이다
        assertEquals(Optional.empty(),
                ColorWords.firstUnsupported("제목은 파란색으로, 혜택 배경은 노란색으로 바꿔줘", benefits));
        assertEquals(Optional.empty(),
                ColorWords.firstUnsupported("제목은 파란색으로 하고 혜택 카드 배경은 노란색으로 해줘", benefits));
        assertEquals(Optional.empty(),
                ColorWords.firstUnsupported("전체 색감을 파랑으로 하고 혜택 배경은 노랑으로", benefits));
    }

    @Test
    @DisplayName("영역을 가리키는 말이 없는 절의 없는 색은 페이지 전체 색이다")
    void 영역_말이_없는_절의_색은_페이지_전체_색이다() {
        Set<Block> benefits = Set.of(Block.BENEFITS);

        // 노랑이 제목 쪽 절에 있다 — 혜택 배경이 아니다
        assertEquals("노랑", ColorWords.firstUnsupported("제목은 노란색으로, 혜택 배경은 파란색으로", benefits)
                .map(u -> u.group().name()).orElse(null));
        // 영역 연산이 없으면 모든 색이 페이지 전체 색이다
        assertEquals("노랑", ColorWords.firstUnsupported("혜택 배경을 노란색으로", Set.of())
                .map(u -> u.group().name()).orElse(null));
        assertEquals("노랑", unsupportedName("바탕색은 노란색으로 바꿔줘"));
    }

    @Test
    @DisplayName("라우터가 짚은 영역의 말만 인정한다 — 다른 영역 말은 영역 배경의 근거가 아니다")
    void 짚지_않은_영역의_말은_근거가_아니다() {
        // 라우터는 혜택만 짚었는데 요청문의 '쿠폰' 절에 노랑이 있다
        assertEquals("노랑", ColorWords.firstUnsupported("제목은 파랑으로, 쿠폰은 노란색으로", Set.of(Block.BENEFITS))
                .map(u -> u.group().name()).orElse(null));
    }

    @Test
    @DisplayName("영역을 가리키는 말 목록에 빠진 편집 가능 영역이 없다 — Block 이 늘면 여기서 걸린다")
    void 영역_말_목록이_모든_편집_영역을_덮는다() {
        for (Block b : Block.values()) {
            if (b == Block.HERO || !b.canEdit()) continue;
            assertTrue(ColorWords.AREA_WORDS.containsKey(b), b.key() + " 의 영역 말이 ColorWords.AREA_WORDS 에 없습니다.");
        }
    }

    @Test
    @DisplayName("null 과 공백은 통과한다")
    void 비어_있으면_통과한다() {
        assertEquals(Optional.empty(), ColorWords.firstUnsupported(null));
        assertEquals(Optional.empty(), ColorWords.firstUnsupported("   "));
    }

    @Test
    @DisplayName("지원하는 색과 없는 색이 같이 있으면 없는 색을 짚는다")
    void 섞이면_없는_색을_짚는다() {
        assertEquals("노랑", unsupportedName("파랑이랑 노랑 섞어서 바꿔줘"));
    }

    @Test
    @DisplayName("안내 문구 — 없는 색 이름, 되는 색 목록, 분위기 길을 담는다")
    void 안내_문구() {
        String msg = ColorWords.message(ColorWords.firstUnsupported("바탕색은 노란색으로 바꿔줘").orElseThrow());

        assertTrue(msg.contains("'노랑' 색감은 지원하지 않습니다"), msg);
        assertTrue(msg.contains("고를 수 있는 색:"), msg);
        assertTrue(msg.contains("파랑") && msg.contains("분홍"), "되는 색이 안 나옵니다: " + msg);
        assertFalse(msg.contains("고를 수 있는 색: 노랑") || msg.contains("· 노랑"), "없는 색이 되는 색 목록에 있습니다: " + msg);
        assertTrue(msg.contains("분위기로 말씀하셔도 됩니다"), msg);
    }

    @Test
    @DisplayName("노랑은 영역 하나의 배경으로는 되므로 그 길을 알려 주고, 하양은 약속하지 않는다")
    void 영역_배경_길은_되는_색만_알려_준다() {
        String yellow = ColorWords.message(ColorWords.firstUnsupported("노란색으로").orElseThrow());
        String white = ColorWords.message(ColorWords.firstUnsupported("하얀색으로").orElseThrow());

        assertTrue(yellow.contains("'혜택 영역 배경을 노란색으로'"), yellow);
        assertFalse(white.contains("영역 하나의 배경"), "되는지 확인하지 않은 길을 약속했습니다: " + white);
    }

    @Test
    @DisplayName("영역 배경 길을 알려 주는 색은 실제 색 이름 변형(v-surface-*)이 있는 색이다")
    void 영역_배경_안내는_변형과_짝이다() {
        for (ColorWords.Group g : ColorWords.GROUPS) {
            if (g.blockHint() == null) continue;
            boolean exists = java.util.Arrays.stream(Variant.values())
                    .anyMatch(v -> v.desc().contains(g.blockHint().replace("색", "") ) || v.desc().contains(g.name()));
            assertTrue(exists, g.name() + " 의 영역 배경 변형이 Variant 에 없습니다. 안내가 거짓이 됩니다.");
        }
    }

    @Test
    @DisplayName("팔레트 설명에서 지원 여부를 읽는다 — 목록을 따로 두지 않는다")
    void 지원_여부는_팔레트_설명에서_읽는다() {
        for (ColorWords.Group g : ColorWords.GROUPS) {
            boolean inSomePalette = Palette.all().stream()
                    .filter(p -> p != Palette.BASE)
                    .anyMatch(p -> g.stems().stream().anyMatch(p.desc()::contains));
            assertEquals(inSomePalette, g.supported(), g.name());
        }
        // BASE 설명의 "기본 남색" 은 지원으로 치지 않는다 — 되돌리기용 설명이다
        assertTrue(ColorWords.supportedNames().contains("남색"), "남색은 겨울 · 새해 팔레트에 있다");
    }
}
