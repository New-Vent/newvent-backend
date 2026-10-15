package com.newvent.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Element;

/**
 * 참여 방식대로 <b>고르는 칸</b>을 그린다 — 예측 선택지 · 복주머니 · 사전예약 전화번호.
 *
 * ★★ 왜 서버가 그리나 — 모델은 참여 설정을 모른다
 *   복주머니가 몇 칸인지, 예측 선택지가 무엇인지는 관리자가 설정 API 로 정한다.
 *   그 값은 생성이 끝난 뒤에 바뀔 수도 있다. HTML 에 굳혀 두면 설정을 고쳐도 화면이 안 바뀐다.
 *   그래서 <b>저장본에는 없고 보여 줄 때 그린다</b> — Slots.fill 과 같은 이유다.
 *
 * ★★ 마크업은 프론트와의 계약이다. 함부로 바꾸면 조용히 깨진다
 *   클래스 이름은 event.css 가 그리고 (.ev-game · .ev-game-choices · .ev-game-choice …),
 *   data-* 는 event-runtime.js 가 읽는다. 양쪽 다 이미 들어와 있다.
 *
 *     SPORTS_PREDICTION   [data-vote]  → runtime 의 collect() 가 { prediction }
 *     LUCKY_POUCH         [data-pouch] → { pouchIndex }
 *     PRE_REGISTRATION    input[type=tel] → { phoneNumber }
 *     BASIC               추가 입력이 없다 — 아무것도 그리지 않는다
 *
 * ★ 고르는 버튼은 {@code data-behavior="vote"} 를 단다
 *   runtime 의 vote 가 <b>같은 부모 안에서</b> 하나에만 active 를 준다. 그래서 선택지는
 *   반드시 한 부모(.ev-game-choices) 아래 형제로 둔다.
 *
 * ★ 설정이 수상하면 아무것도 그리지 않는다
 *   ConfiguredGame 이 검증기를 통과한 설정만 넘기지만, 여기서도 모양을 한 번 더 본다.
 *   반쯤 그려진 칸을 내보내면 사용자가 고를 수 없는 버튼을 누른다.
 */
public final class GameWidget {

    private GameWidget() { }

    /** 바깥 상자 — event.css 가 이 이름으로 그린다 */
    public static final String ROOT_CLASS = "ev-game";

    /**
     * 참여 방식대로 {@code at} 의 내용을 채운다. 그릴 게 없으면 비운 채로 둔다.
     *
     * @param at       게임 자리 — {@code [data-behavior~="game"]}
     * @param gameCode 참여 방식
     * @param config   그 방식의 설정
     * @return 그렸으면 true
     */
    public static boolean draw(Element at, String gameCode, Map<String, Object> config) {
        at.empty();
        at.addClass(ROOT_CLASS);

        Map<String, Object> values = config == null ? Map.of() : config;

        return switch (gameCode == null ? "" : gameCode) {
            case "SPORTS_PREDICTION" -> choices(at, "예측을 골라 주세요", "data-vote", predictions(values));
            case "LUCKY_POUCH" -> choices(at, "복주머니를 골라 주세요", "data-pouch", pouches(values));
            case "PRE_REGISTRATION" -> phone(at);
            default -> false;                       // BASIC · 모르는 방식 — 추가 입력이 없다
        };
    }

    /** 고르는 버튼 묶음. 값이 하나도 없으면 그리지 않는다 */
    private static boolean choices(Element at, String title, String valueAttr, List<Choice> items) {
        if (items.isEmpty()) {
            return false;
        }

        at.appendElement("span").addClass("ev-game-title").text(title);
        Element box = at.appendElement("div").addClass("ev-game-choices");

        for (Choice c : items) {
            box.appendElement("button")
                    .addClass("ev-game-choice")
                    .attr("type", "button")
                    .attr("aria-pressed", "false")
                    .attr(Behavior.ATTR, Behavior.VOTE.key())
                    .attr(valueAttr, c.value())
                    .text(c.label());
        }
        return true;
    }

    /** 사전예약 전화번호 칸. 검증은 runtime(모양)과 서버(ParticipationValidator)가 각각 한다 */
    private static boolean phone(Element at) {
        at.appendElement("span").addClass("ev-game-title").text("휴대폰 번호를 입력해 주세요");
        at.appendElement("label")
                .addClass("ev-game-phone")
                .appendElement("input")
                .addClass("ev-game-phone-input")
                .attr("type", "tel")
                .attr("inputmode", "numeric")
                .attr("maxlength", "13")
                .attr("placeholder", "01012345678")
                .attr("aria-label", "휴대폰 번호");
        return true;
    }

    private record Choice(String value, String label) { }

    /**
     * 예측 선택지. 고르는 값과 보이는 글자가 같다.
     *
     * ★ 참여 API 가 {@code predictionOptions.contains(prediction)} 으로 거르므로
     *   보낸 값이 그대로 선택지여야 한다 (ParticipationValidator.predictionInput).
     */
    private static List<Choice> predictions(Map<String, Object> config) {
        if (!(config.get("predictionOptions") instanceof List<?> options)) {
            return List.of();
        }

        List<Choice> out = new ArrayList<>(options.size());
        for (Object option : options) {
            if (option instanceof String text && !text.isBlank()) {
                out.add(new Choice(text, text));
            }
        }
        return out;
    }

    /**
     * 복주머니 칸. 고르는 값은 번호이고 보이는 글자는 "N번" 이다.
     *
     * ★★ 경품명을 글자로 쓰면 안 된다 — 고르기 전에 무엇이 들었는지 보이면 추첨이 아니다.
     *   설정에는 칸마다 prizeName 이 있지만 그건 결과에만 쓴다.
     *
     * ★ 번호순으로 세운다. 설정의 배열 순서는 관리자가 입력한 순서라 뒤섞일 수 있다.
     */
    private static List<Choice> pouches(Map<String, Object> config) {
        if (!(config.get("pouches") instanceof List<?> pouches)) {
            return List.of();
        }

        List<Integer> indexes = new ArrayList<>(pouches.size());
        for (Object item : pouches) {
            if (item instanceof Map<?, ?> pouch && pouch.get("pouchIndex") instanceof Number n) {
                indexes.add(n.intValue());
            }
        }
        indexes.sort(Comparator.naturalOrder());

        List<Choice> out = new ArrayList<>(indexes.size());
        for (Integer index : indexes) {
            out.add(new Choice(String.valueOf(index), index + "번"));
        }
        return out;
    }
}
