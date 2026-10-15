package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 게임 UI 마크업은 <b>프론트와의 계약</b>이다 — event.css 가 그리고 event-runtime.js 가 읽는다.
 * 이름을 바꾸면 조용히 깨지므로 여기서 고정한다.
 */
class GameWidgetTest {

    private Element slot() {
        Document doc = Jsoup.parseBodyFragment("<div></div>");
        return doc.body().child(0);
    }

    @Test
    @DisplayName("예측형 — 선택지마다 버튼 하나, data-vote 에 보낼 값이 그대로 들어간다")
    void 예측형을_그린다() {
        Element at = slot();

        assertTrue(GameWidget.draw(at, "SPORTS_PREDICTION",
                Map.of("predictionOptions", List.of("대한민국 승", "무승부", "상대팀 승"))));

        assertTrue(at.hasClass("ev-game"));
        assertEquals(3, at.select(".ev-game-choices .ev-game-choice").size());
        assertEquals(List.of("대한민국 승", "무승부", "상대팀 승"), at.select("[data-vote]").eachAttr("data-vote"));

        // ★ 참여 API 가 predictionOptions.contains(prediction) 으로 거른다 — 보낼 값이 선택지 그대로여야 한다
        Element first = at.selectFirst(".ev-game-choice");
        assertEquals("대한민국 승", first.text());
        assertEquals("vote", first.attr("data-behavior"));
    }

    @Test
    @DisplayName("★ 고르는 버튼은 한 부모 아래 형제여야 한다 — runtime 의 vote 가 형제끼리만 고른다")
    void 선택지는_한_부모_아래에_있다() {
        Element at = slot();
        GameWidget.draw(at, "SPORTS_PREDICTION", Map.of("predictionOptions", List.of("A", "B")));

        Element box = at.selectFirst(".ev-game-choices");
        assertEquals(2, box.children().size(), "선택지가 같은 부모의 형제가 아니면 하나만 골라지지 않는다");
    }

    @Test
    @DisplayName("복주머니 — 번호순으로 세우고 경품명은 쓰지 않는다")
    void 복주머니를_그린다() {
        Element at = slot();

        // 설정 순서를 일부러 뒤섞는다 — 관리자가 입력한 순서다
        assertTrue(GameWidget.draw(at, "LUCKY_POUCH", Map.of("pouches", List.of(
                Map.of("pouchIndex", 2, "winProbability", 10, "prizeName", "커피 쿠폰"),
                Map.of("pouchIndex", 1, "winProbability", 30, "prizeName", "한우세트")))));

        assertEquals(List.of("1", "2"), at.select("[data-pouch]").eachAttr("data-pouch"));
        assertEquals(List.of("1번", "2번"), at.select(".ev-game-choice").eachText());

        // ★★ 고르기 전에 무엇이 들었는지 보이면 추첨이 아니다
        assertFalse(at.html().contains("한우세트"), "경품명이 새면 안 된다: " + at.html());
        assertFalse(at.html().contains("커피 쿠폰"), "경품명이 새면 안 된다: " + at.html());
    }

    @Test
    @DisplayName("사전예약 — runtime 이 찾는 input[type=tel] 을 그린다")
    void 사전예약을_그린다() {
        Element at = slot();

        assertTrue(GameWidget.draw(at, "PRE_REGISTRATION", Map.of()));

        Element input = at.selectFirst("input[type=tel]");
        assertEquals("ev-game-phone-input", input.className());
        assertEquals("01012345678", input.attr("placeholder"));
    }

    @Test
    @DisplayName("기본형은 그릴 게 없다 — 추가 입력이 없는 방식이다")
    void 기본형은_그리지않는다() {
        assertFalse(GameWidget.draw(slot(), "BASIC", Map.of("resultMode", "IMMEDIATE")));
    }

    @Test
    @DisplayName("설정이 비거나 모양이 틀리면 그리지 않는다 — 고를 수 없는 버튼을 내보내지 않는다")
    void 설정이_수상하면_그리지않는다() {
        assertFalse(GameWidget.draw(slot(), "SPORTS_PREDICTION", Map.of()));
        assertFalse(GameWidget.draw(slot(), "SPORTS_PREDICTION", Map.of("predictionOptions", List.of())));
        assertFalse(GameWidget.draw(slot(), "SPORTS_PREDICTION", Map.of("predictionOptions", "문자열")));
        assertFalse(GameWidget.draw(slot(), "LUCKY_POUCH", Map.of("pouches", List.of("칸이 아님"))));
        assertFalse(GameWidget.draw(slot(), null, Map.of()));
        assertFalse(GameWidget.draw(slot(), "모르는방식", Map.of()));
    }

    @Test
    @DisplayName("다시 그리면 앞의 것이 남지 않는다 — 설정을 바꿔도 칸이 쌓이지 않는다")
    void 다시_그리면_비우고_그린다() {
        Element at = slot();
        GameWidget.draw(at, "SPORTS_PREDICTION", Map.of("predictionOptions", List.of("A", "B", "C")));
        GameWidget.draw(at, "SPORTS_PREDICTION", Map.of("predictionOptions", List.of("A")));

        assertEquals(1, at.select(".ev-game-choice").size());
    }

    @Test
    @DisplayName("data-vote · data-pouch 는 정화 허용 목록에 있어야 한다 — 없으면 고른 값이 지워진다")
    void 고른_값의_속성이_허용된다() {
        assertTrue(Behavior.ATTRIBUTES.contains("data-vote"));
        assertTrue(Behavior.ATTRIBUTES.contains("data-pouch"));
    }
}
