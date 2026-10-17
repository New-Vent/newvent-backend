package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 확장 항목 고르기({@link KeywordMatch}) — 팔레트 · 변형 공통 규칙.
 *
 * ★ 변형 확장은 아직 하나도 없다. 그래서 규칙은 여기서 가짜 항목으로 본다 —
 *   변형 확장이 들어와도 이 규칙대로 실린다.
 */
class KeywordMatchTest {

    private record Item(String cssClass, String desc, boolean core) implements Pickable {
    }

    private static final Item CORE = new Item("v-core", "영수증 — 기본인데 이름이 맞는다", true);
    private static final Item RECEIPT = new Item("v-receipt", "영수증 — 영수증 모양 목록", false);
    private static final Item BOARDING = new Item("v-boarding", "항공권 — 비행기 탑승권 모양", false);
    private static final Item STACK = new Item("v-stack", "겹친 카드 — 겹쳐 쌓인 카드", false);
    private static final Item PURPLE = new Item("v-purple", "보라색 — 보라 단색", false);
    private static final Item NO_DASH = new Item("v-nodash", "폴라로이드 사진 틀", false);

    private static final List<Item> ALL = List.of(CORE, RECEIPT, BOARDING, STACK, PURPLE, NO_DASH);
    private static final Set<String> NOT = Set.of("카드", "보라");

    private static List<Item> names(String text) {
        return KeywordMatch.matched(ALL, text, 10, NOT, false);
    }

    @Test
    @DisplayName("요청문이 비었거나 맞는 이름이 없으면 빈 목록")
    void 빈_요청문() {
        assertEquals(List.of(), names(null));
        assertEquals(List.of(), names("  "));
        assertEquals(List.of(), names("신규 가입 이벤트"));
    }

    @Test
    @DisplayName("기본(core)은 이름이 맞아도 고르지 않는다 — 이미 목록에 있다")
    void 기본은_안_고른다() {
        assertEquals(List.of(RECEIPT), names("혜택을 영수증 모양으로"));
    }

    @Test
    @DisplayName("흔한 말(카드 · 보라색)은 열쇠말이 아니다 — 끝의 '색' 을 떼고도 본다")
    void 흔한_말() {
        assertEquals(List.of(), names("카드 혜택을 보라색으로"));
    }

    @Test
    @DisplayName("이름만 볼 때는 설명의 말이 맞아도 고르지 않는다")
    void 이름만_본다() {
        assertEquals(List.of(), names("비행기 탑승권 느낌으로"));
        assertEquals(List.of(BOARDING), KeywordMatch.matched(ALL, "비행기 탑승권 느낌으로", 10, NOT, true));
    }

    @Test
    @DisplayName("많이 맞은 것부터, 같으면 선언 순서, 최대 개수를 넘지 않는다")
    void 순서와_상한() {
        assertEquals(List.of(RECEIPT, BOARDING), names("항공권이랑 영수증 모양"));
        // 이름 3점 + 설명 말마다 1점 — 항공권 3+2(비행기 · 탑승권), 영수증 3+1(영수증)
        assertEquals(List.of(BOARDING, RECEIPT),
                KeywordMatch.matched(ALL, "항공권 비행기 탑승권 영수증", 10, NOT, true));
        assertEquals(List.of(RECEIPT), KeywordMatch.matched(ALL, "항공권이랑 영수증 모양", 1, NOT, false));
    }

    @Test
    @DisplayName("'이름 — 설명' 꼴이 아니면 desc 전체가 이름이다")
    void 대시_없는_설명() {
        assertEquals(List.of(NO_DASH), names("폴라로이드 느낌"));
    }

    @Test
    @DisplayName("같은 요청문이면 늘 같은 목록이다 — 재시도가 흔들리지 않는다")
    void 결정적이다() {
        assertEquals(names("영수증 항공권 폴라로이드"), names("영수증 항공권 폴라로이드"));
    }
}
