package com.newvent.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 관리자가 말한 **색 이름**이 지금 팔레트로 낼 수 있는 색인가.
 *
 * ★ 왜 서버가 정하나
 *   "바탕색은 노란색으로" 에 노란 팔레트가 없다. 모델에게 "없으면 고르지 마라" 고 시켜도
 *   봄(분홍) · 여름(파랑)을 골라 놓고 "반영했어요" 를 냈다(Bedrock 실측). 프롬프트로는 못 막았다.
 *   그래서 모델이 팔레트를 고르기 전에 서버가 단어로 거른다.
 *
 * ★ 지원 여부는 팔레트 설명에서 읽는다
 *   {@link Palette#desc()} 에 그 색 이름이 적혀 있으면 지원이다. 이 파일에 "되는 색" 목록을 따로 두지 않으므로
 *   팔레트를 추가하면(예: 노랑) 설명에 "노랑" 만 적으면 된다 — 안내 문구가 저절로 사라진다.
 *
 * ★ 이 검사는 페이지 전체 색감 경로(hero STYLE)에서만 쓴다
 *   영역 하나의 배경은 {@link Variant} 의 색 이름 변형(v-surface-yellow …)이 따로 받는다.
 *   그쪽은 막지 않는다 — 그래서 안내 문구가 그 길을 같이 알려 준다.
 *
 * ★ 한계 — 사전에 없는 색 단어(카키 · 자홍 …)는 걸러지지 않고 모델 경로로 간다.
 */
public final class ColorWords {

    private ColorWords() { }

    /**
     * 색 하나. 같은 색을 가리키는 말을 묶는다.
     *
     * @param name      안내 문구에 쓸 대표 이름
     * @param words     관리자가 쓰는 말 (어간 포함 — 노란 · 노랗 · 노랑)
     * @param stems     팔레트 설명에서 찾을 말. 하나라도 들어 있으면 지원
     * @param blockHint 영역 하나의 배경으로는 되는 색이면 안내 예시에 쓸 말(노란색), 아니면 null.
     *                  {@link Variant} 의 색 이름 변형(v-surface-yellow …)과 짝이다
     */
    public record Group(String name, List<String> words, List<String> stems, String blockHint) {

        /** 팔레트(BASE 제외) 설명에 이 색이 적혀 있는가 */
        public boolean supported() {
            for (Palette p : Palette.all()) {
                if (p == Palette.BASE) continue;
                for (String stem : stems) {
                    if (p.desc().contains(stem)) return true;
                }
            }
            return false;
        }
    }

    // ★ 순서가 안내 문구의 순서다. 무지개 순서 → 무채색
    static final List<Group> GROUPS = List.of(
            new Group("빨강",   List.of("빨강", "빨간", "빨갛", "붉은", "붉게", "레드"),      List.of("빨강"),   null),
            new Group("주황",   List.of("주황", "오렌지"),                               List.of("주황"),   null),
            new Group("노랑",   List.of("노랑", "노란", "노랗", "옐로우", "옐로"),         List.of("노랑"),   "노란색"),
            new Group("연두",   List.of("연두"),                                         List.of("연두"),   null),
            new Group("초록",   List.of("초록", "녹색", "그린"),                          List.of("초록"),   null),
            new Group("청록",   List.of("청록", "에메랄드"),                              List.of("청록"),   null),
            new Group("하늘색", List.of("하늘색", "하늘빛", "스카이블루"),                  List.of("하늘색"), null),
            new Group("파랑",   List.of("파랑", "파란", "파랗", "블루"),                   List.of("파랑"),   null),
            new Group("남색",   List.of("남색", "네이비"),                               List.of("남색"),   null),
            new Group("보라",   List.of("보라", "퍼플", "자주"),                          List.of("보라"),   null),
            new Group("분홍",   List.of("분홍", "핑크"),                                 List.of("분홍"),   null),
            new Group("마젠타", List.of("마젠타", "자홍"),                               List.of("마젠타"), null),
            new Group("코랄",   List.of("코랄"),                                         List.of("코랄"),   null),
            new Group("버건디", List.of("버건디", "와인색"),                              List.of("버건디"), null),
            new Group("민트",   List.of("민트"),                                         List.of("민트"),   null),
            new Group("갈색",   List.of("갈색", "브라운"),                                List.of("갈색"),   null),
            new Group("크림",   List.of("크림", "베이지", "아이보리"),                     List.of("크림"),   null),
            new Group("살구",   List.of("살구"),                                         List.of("살구"),   null),
            new Group("금색",   List.of("금색", "금빛", "골드"),                          List.of("금색"),   null),
            new Group("은색",   List.of("은색", "은빛", "실버"),                          List.of("은빛"),   null),
            new Group("검정",   List.of("검정", "검은", "까만", "까맣", "블랙", "흑색"),    List.of("검정"),   null),
            new Group("회색",   List.of("회색", "그레이"),                                List.of("회색"),   null),
            new Group("하양",   List.of("하양", "하얀", "하얗", "흰", "화이트"),           List.of("하양"),   null)
    );

    // 색 이름 뒤: 꼬리(색 · 계열 …) → 조사/어미 → 그 뒤에 한글이 더 오면 다른 낱말이다("블루투스" · "그린다")
    private static final String SUFFIX = "(?:색상|색감|색|빛깔|빛|계열|톤|컬러)?";
    private static final String PARTICLE = "(?:으로만|으로|로만|로|은|는|이|가|을|를|의|도|만|과|와|처럼|같은|게)?(?![가-힣])";

    private static final List<Pattern> PATTERNS = GROUPS.stream()
            .map(grp -> {
                // 긴 말이 먼저 — "하늘색" 이 짧은 말보다 먼저 잡혀야 한다
                String alt = grp.words().stream()
                        .sorted(Comparator.comparingInt(String::length).reversed())
                        .map(Pattern::quote)
                        .reduce((a, b) -> a + "|" + b).orElseThrow();
                return Pattern.compile("(?:" + alt + ")" + SUFFIX + PARTICLE);
            })
            .toList();

    /** 지원하지 않는 색을 말한 경우 */
    public record Unsupported(Group group) { }

    /**
     * 요청문에서 팔레트로 낼 수 없는 색 이름을 찾는다. 요청문에서 가장 앞에 나온 것 하나.
     *
     * @return 지원하지 않는 색이 없으면 빈 값 — 색 말이 없는 요청과 지원하는 색만 있는 요청
     */
    public static Optional<Unsupported> firstUnsupported(String requestText) {
        if (requestText == null || requestText.isBlank()) return Optional.empty();
        Group best = null;
        int bestAt = Integer.MAX_VALUE;
        for (int i = 0; i < GROUPS.size(); i++) {
            Group grp = GROUPS.get(i);
            if (grp.supported()) continue;
            Matcher m = PATTERNS.get(i).matcher(requestText);
            if (m.find() && m.start() < bestAt) {
                bestAt = m.start();
                best = grp;
            }
        }
        return Optional.ofNullable(best).map(Unsupported::new);
    }

    /** 지금 팔레트로 낼 수 있는 색 이름들 */
    public static List<String> supportedNames() {
        List<String> out = new ArrayList<>();
        for (Group grp : GROUPS) if (grp.supported()) out.add(grp.name());
        return out;
    }

    /**
     * 되묻는 문장 — 관리자에게 그대로 보인다.
     *
     * ★ 안 되는 것만 말하면 막힌다. 되는 색 · 분위기 · (되는 색이면) 영역 하나의 배경 길을 같이 준다
     */
    public static String message(Unsupported u) {
        StringBuilder s = new StringBuilder();
        s.append("'").append(u.group().name()).append("' 색감은 지원하지 않습니다. ")
                .append("고를 수 있는 색: ").append(String.join(" · ", supportedNames())).append(". ")
                .append("봄 · 여름 · 가을 · 겨울 · 밤 · 숲 같은 분위기로 말씀하셔도 됩니다.");
        if (u.group().blockHint() != null) {
            s.append(" 영역 하나의 배경만 바꾸려면 '혜택 영역 배경을 ").append(u.group().blockHint())
                    .append("으로' 처럼 영역을 골라 말씀해 주세요.");
        }
        return s.toString();
    }
}
