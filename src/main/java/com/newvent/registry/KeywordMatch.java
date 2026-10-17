package com.newvent.registry;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 요청문이 부른 확장 항목을 고른다 — 팔레트 · 변형 공통 ({@link Pickable}).
 *
 * ★ 왜 공통인가
 *   팔레트에서 먼저 만들었다(Palette.matched). 변형도 같은 문제를 겪는다 —
 *   생성 프롬프트에는 블록마다 표본 몇 개만 실려서(Variant.sample), 확장을 늘려도 모델 앞에 거의 안 나온다.
 *   규칙이 두 벌이 되면 언젠가 갈라진다. 한 벌로 둔다.
 *
 * ★ 점수 — 이름(— 앞)의 말이 맞으면 {@link #NAME_WEIGHT}점, 설명(— 뒤)의 말이 맞으면 1점.
 *   설명을 볼지는 부르는 쪽이 정한다. 변형은 설명에 "카드 · 목록" 같은 흔한 말이 많아 이름만 본다.
 * ★ 많이 맞은 것부터, 같으면 선언 순서. 같은 요청문이면 늘 같은 목록이다 — 재시도가 흔들리지 않는다.
 */
final class KeywordMatch {

    static final int NAME_WEIGHT = 3;

    private KeywordMatch() {
    }

    /**
     * @param candidates  후보 — 이 순서가 동점일 때의 순서다. 기본(core)은 알아서 뺀다
     * @param requestText 관리자 요청문. 비었으면 빈 목록
     * @param max         최대 개수
     * @param notKeywords 열쇠말로 쓰지 않는 말 — 어느 요청에나 나와서 아무거나 끌어온다
     * @param descWords   설명(— 뒤)의 말도 점수에 넣나
     */
    static <T extends Pickable> List<T> matched(List<T> candidates, String requestText, int max,
                                                Set<String> notKeywords, boolean descWords) {
        if (requestText == null || requestText.isBlank()) return List.of();
        String text = requestText.toLowerCase(Locale.ROOT);
        return IntStream.range(0, candidates.size())
                .filter(i -> !candidates.get(i).core())
                .mapToObj(i -> Map.entry(i, score(candidates.get(i).desc(), text, notKeywords, descWords)))
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparing((Map.Entry<Integer, Integer> e) -> -e.getValue())
                        .thenComparing(Map.Entry::getKey))
                .limit(max)
                .map(e -> candidates.get(e.getKey()))
                .toList();
    }

    private static int score(String desc, String text, Set<String> notKeywords, boolean descWords) {
        int at = desc.indexOf(" — ");
        String name = at < 0 ? desc : desc.substring(0, at);
        String rest = at < 0 ? "" : desc.substring(at + 3);
        return NAME_WEIGHT * hits(name, text, notKeywords) + (descWords ? hits(rest, text, notKeywords) : 0);
    }

    private static int hits(String part, String text, Set<String> notKeywords) {
        int n = 0;
        for (String w : part.toLowerCase(Locale.ROOT).split("[\\s,·()+/]+")) {
            // ★ "보라색" 도 흔한 색이다 — 끝의 "색" 을 떼고 본다
            String bare = w.endsWith("색") && w.length() > 2 ? w.substring(0, w.length() - 1) : w;
            if (w.length() < 2 || notKeywords.contains(w) || notKeywords.contains(bare)) continue;
            if (text.contains(w)) n++;
        }
        return n;
    }
}
