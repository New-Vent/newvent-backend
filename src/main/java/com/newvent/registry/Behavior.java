package com.newvent.registry;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 이벤트 페이지의 동작 — 요소에 {@code data-behavior="이름"} 으로 선언하고, 실행은 runtime.js 가 한다.
 *
 * ★ 이름 목록의 단일 출처다
 *   runtime.js(newvent-frontend public/assets/event-runtime.js)의 핸들러 표와 짝이다.
 *   여기에 추가하면 runtime.js 에도 핸들러를 추가한다. 한쪽만 있으면
 *   정화에서 지워지거나(여기 없음), 눌러도 아무 일이 없다(runtime 없음).
 *
 * ★ HTML 에는 "무엇을" 만 적는다. "어디로 · 어떻게" 는 runtime.js 가 정한다
 *   요청 URL · 메서드 · 본문 형식은 runtime.js 에 고정이다. data-endpoint · data-url 같은 속성은
 *   허용 목록({@link #ATTRIBUTES})에 없어서 정화가 지운다. HTML 에 무엇이 섞여 와도 요청이 갈 곳은 runtime 이 아는 곳뿐이다.
 *
 * ★ 모델은 behavior 를 만들지 않는다
 *   생성 정화(sanitizeGenerated)는 data-behavior 를 지우고 프롬프트에도 노출하지 않는다.
 *   필요한 것은 서버가 심는다. 수정에서는 원본의 것을 그대로 두게 하고 checkPreserved 가 대조한다.
 *
 * ★ 한 요소에 여럿을 공백으로 적을 수 있다 — {@code data-behavior="vote toast"}. 적힌 순서대로 실행한다
 */
public enum Behavior {

    /** 안내 문구를 띄운다 — data-demo-msg */
    TOAST("toast"),

    /** id 가 data-target 인 요소로 스크롤하고, 그 안의 [data-shake] 를 흔든다. data-state="completed" 면 건너뛴다 */
    SCROLL_TO("scroll-to"),

    /** 가까운 [data-shake] 요소를 흔든다 */
    SHAKE("shake"),

    /**
     * 열기 — 누른 요소를 완료 상태로 바꾼다(class opened, 문구 data-done-text).
     * data-target 의 id 들도 완료 상태로 바꾼다(data-state="completed", 문구 data-done-text, 안내 data-done-msg)
     */
    OPEN("open"),

    /** 남은 시간을 센다 — data-until(ISO 시각) 이 있으면 그때까지, 없으면 data-seconds 초 데모 */
    COUNTDOWN("countdown"),

    /** 참여자 수처럼 늘어나는 숫자 — 데모 */
    COUNTER("counter"),

    /** 같은 묶음의 투표 버튼 중 하나를 고른다 — data-vote. 실제 집계 API 는 후속 */
    VOTE("vote"),

    /** 참여 — 참여 방식(body data-game)에 맞는 본문으로 참여 API 를 부른다 (runtime → 사용자 앱 위임) */
    PARTICIPATE("participate"),

    /**
     * 게임 자리 — 투표 · 복주머니 · 사전예약 입력이 들어갈 곳. **runtime 핸들러가 없다.**
     * 안은 보여 줄 때 서버가 참여 설정(EventGameConfig)으로 그린다 — BehaviorPlanter.bind
     */
    GAME("game");

    /**
     * LLM(동작 배치기)이 고를 수 있는 것 — BehaviorGate 가 이 밖의 것은 버린다.
     *
     * ★ 나머지(toast · shake · open · vote)는 템플릿 · 서버만 쓴다.
     *   vote · open 은 게임 자리 안에서 서버가 만들고, toast · shake 는 템플릿 연출이다
     */
    public static final Set<Behavior> PLANNABLE =
            EnumSet.of(COUNTDOWN, COUNTER, SCROLL_TO, PARTICIPATE, GAME);

    /** 요소에 선언하는 속성 */
    public static final String ATTR = "data-behavior";

    /** 흔들 대상 표시 — 값 없는 속성이다 */
    public static final String SHAKE_MARK = "data-shake";

    /**
     * behavior 가 읽는 data-* — 수정 · 템플릿 경로의 정화가 이것만 연다.
     *
     * ★ 허용 목록이다. 여기 없는 data-* (data-endpoint · data-url · data-method …)는 정화가 지운다
     */
    public static final List<String> ATTRIBUTES = List.of(
            ATTR, SHAKE_MARK,
            "data-demo-msg", "data-target", "data-state",
            "data-done-text", "data-done-msg",
            "data-until", "data-seconds",
            "data-vote");

    private final String key;

    Behavior(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<Behavior> find(String key) {
        if (key == null) return Optional.empty();
        String k = key.trim();
        return Arrays.stream(values()).filter(b -> b.key.equals(k)).findFirst();
    }

    /** 공백으로 나눈 값에서 목록에 있는 것만, 적힌 순서대로. 중복은 하나로 */
    public static List<Behavior> parse(String value) {
        if (value == null || value.isBlank()) return List.of();
        Set<Behavior> out = new LinkedHashSet<>();
        for (String t : value.trim().split("\\s+")) find(t).ifPresent(out::add);
        return List.copyOf(out);
    }

    /**
     * 목록에 없는 이름을 걷어낸다. 남는 게 없으면 속성째 지운다. **정화에서만 부른다.**
     */
    public static void sanitize(Document doc) {
        for (Element el : doc.select("[" + ATTR + "]")) {
            List<Behavior> kept = parse(el.attr(ATTR));
            if (kept.isEmpty()) el.removeAttr(ATTR);
            else el.attr(ATTR, kept.stream().map(Behavior::key).collect(Collectors.joining(" ")));
        }
    }

    /** 이 HTML 에 선언된 behavior 이름들 — 수정 전후 대조용 (Slots.keysOf 와 같은 방식) */
    public static Set<String> keysOf(String html) {
        Set<String> keys = new LinkedHashSet<>();
        for (Element el : Jsoup.parseBodyFragment(html == null ? "" : html).select("[" + ATTR + "]")) {
            for (Behavior b : parse(el.attr(ATTR))) keys.add(b.key);
        }
        return keys;
    }
}
