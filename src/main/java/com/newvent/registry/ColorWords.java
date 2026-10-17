package com.newvent.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 *   한 요청에 둘이 섞이면("전체는 파랑으로 바꾸고 혜택 배경은 노랑으로") 색마다 바로 앞의 대상 말로 가른다 —
 *   영역을 가리키는 말이 가장 가까우면 영역 배경으로 보고 세지 않는다. 라우터가 그 영역을 짚었을 때만 그렇게 본다.
 *
 * ★ 피하려고 말한 색은 세지 않는다 — "노란색 말고 파란색으로" 는 파랑 요청이다.
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

    /**
     * 색 이름 바로 뒤에 붙어 "그 색은 원하지 않는다" 는 뜻을 만드는 말.
     * "노란색 말고 파란색으로" · "노란색 빼고" · "노란색 대신" · "노란색은 싫어" · "노란색이 아니라 분홍색으로"
     *
     * ★ 이런 색은 거절하면 안 된다. 관리자는 그 색을 **피하려고** 말한 것이다(PR 리뷰).
     */
    private static final Pattern NEGATED_AFTER = Pattern.compile(
            "^\\s*(?:색상|색감|색|빛깔|빛|계열|톤|컬러)?\\s*(?:으로|로|은|는|이|가|도|만)?\\s*"
            + "(?:말고|말구|빼고|제외|아닌|아니라|아니고|아니면|대신|싫"
            + "|쓰지\\s*(?:말|마)|하지\\s*(?:말|마)|넣지\\s*(?:말|마)|안\\s*(?:쓰|써|넣|들어))");

    /** 문장 경계. 색의 대상은 같은 문장 안에서만 찾는다 (쉼표는 경계가 아니다) */
    private static final Pattern SENTENCE_BREAK = Pattern.compile("[.!?\\n]");

    /**
     * 페이지 전체(= hero 쪽)를 가리키는 말. 색 앞에 이 말이 가장 가까우면 그 색은 페이지 전체 색이다.
     *
     * ★ "바탕" · "배경" 은 넣지 않았다. 영역에도 붙는 말이라("혜택 배경") 대상을 정하지 못한다.
     */
    private static final Pattern PAGE_WORDS = Pattern.compile("전체|전부|모두|페이지|제목|히어로|색감|테마|분위기");

    /**
     * 영역을 가리키는 말. 색 앞에 이 말이 가장 가까우면 그 색은 **영역 하나의 배경**이다.
     *
     * ★ 영역 배경은 {@link Variant} 의 색 이름 변형(v-surface-yellow …)이 받으므로 팔레트 검사 대상이 아니다.
     *   hero 는 없다 — 제목 영역은 배경색 변형이 없고, 제목에 건 색은 페이지 전체 색감 경로로 간다.
     * ★ Block 이 늘면 여기도 늘려야 한다 — ColorWordsTest 가 빠진 영역을 잡는다.
     */
    static final Map<Block, Pattern> AREA_WORDS = Map.ofEntries(
            Map.entry(Block.HIGHLIGHT, Pattern.compile("하이라이트|강조 ?(?:띠|배너|영역)|띠 ?배너")),
            Map.entry(Block.INTRO, Pattern.compile("소개|인트로")),
            Map.entry(Block.STATS, Pattern.compile("통계|숫자 ?영역|스탯")),
            Map.entry(Block.BENEFITS, Pattern.compile("혜택|베네핏|카드")),
            Map.entry(Block.PRIZE, Pattern.compile("경품|상품")),
            Map.entry(Block.COUPON, Pattern.compile("쿠폰")),
            Map.entry(Block.COMPARE, Pattern.compile("비교")),
            Map.entry(Block.AUDIENCE, Pattern.compile("대상")),
            Map.entry(Block.STEPS, Pattern.compile("참여 ?방법|단계|스텝")),
            Map.entry(Block.SCHEDULE, Pattern.compile("일정|스케줄|타임라인")),
            Map.entry(Block.FAQ, Pattern.compile("FAQ|자주 ?묻|질문|Q&A", Pattern.CASE_INSENSITIVE)),
            Map.entry(Block.CTA, Pattern.compile("버튼|신청 ?하기|참여 ?하기|CTA", Pattern.CASE_INSENSITIVE)));

    /** 지원하지 않는 색을 말한 경우 */
    public record Unsupported(Group group) { }

    /** 영역 연산이 없는 요청 — 모든 색을 페이지 전체 색으로 본다 */
    public static Optional<Unsupported> firstUnsupported(String requestText) {
        return firstUnsupported(requestText, Set.of());
    }

    /**
     * 요청문에서 **페이지 전체 색감으로 말한**, 팔레트로 낼 수 없는 색 이름을 찾는다. 요청문에서 가장 앞에 나온 것 하나.
     *
     * 다음 색은 세지 않는다.
     *   · "노란색 말고 파란색으로" 처럼 그 색을 피하려는 말({@link #NEGATED_AFTER})
     *   · 영역 하나의 배경으로 말한 색 — "혜택 배경은 노랑으로". 색 앞(같은 문장)에서 **가장 가까운 대상 말**이
     *     라우터가 짚은 영역을 가리키는 말이면 그렇다. 페이지 말("전체", "제목")이 더 가까우면 페이지 전체 색이다.
     *
     * ★ 연결어("~하고 · ~바꾸고 · ~하며 · ~해서")로 문장을 자르지 않는다. 어미가 너무 다양해서 놓친다 —
     *   "전체는 노란색으로 바꾸고 혜택 배경은 파란색으로" 가 한 덩어리가 되어 전체 노랑이 영역 배경으로 통과했다(PR 리뷰).
     *   색마다 바로 앞의 대상 말을 본다.
     *
     * @param areaBlocks 라우터가 STYLE 로 짚은 hero 가 아닌 영역들. 비어 있으면 모든 색이 페이지 전체 색이다
     * @return 지원하지 않는 색이 없으면 빈 값 — 색 말이 없는 요청과 지원하는 색만 있는 요청
     */
    public static Optional<Unsupported> firstUnsupported(String requestText, Collection<Block> areaBlocks) {
        if (requestText == null || requestText.isBlank()) return Optional.empty();
        Group best = null;
        int bestAt = Integer.MAX_VALUE;
        for (int i = 0; i < GROUPS.size(); i++) {
            Group grp = GROUPS.get(i);
            if (grp.supported()) continue;
            Matcher m = PATTERNS.get(i).matcher(requestText);
            while (m.find()) {
                if (m.start() >= bestAt) break;
                if (NEGATED_AFTER.matcher(requestText.substring(m.end())).find()) continue;
                if (isAreaScoped(requestText, m.start(), areaBlocks)) continue;
                bestAt = m.start();
                best = grp;
                break;
            }
        }
        return Optional.ofNullable(best).map(Unsupported::new);
    }

    /**
     * 이 위치의 색이 영역 하나의 배경으로 말한 색인가 — 색 앞(같은 문장)에서 가장 가까운 대상 말이 영역 말인가.
     *
     *   · 앞에 영역 말이 없다                     → 페이지 전체 색 ("노란색으로 바꿔줘", "전체는 노랑으로")
     *   · 영역 말이 페이지 말보다 가깝다          → 영역 배경 ("전체는 파랑, 혜택 배경은 노랑")
     *   · 페이지 말이 더 가깝다                   → 페이지 전체 색 ("혜택 배경은 파랑, 전체는 노랑")
     *   · 단 "혜택 전체" 처럼 영역 말 바로 뒤에 붙은 페이지 말은 그 영역을 꾸미는 말이라 영역으로 본다
     */
    private static boolean isAreaScoped(String text, int at, Collection<Block> areaBlocks) {
        if (areaBlocks == null || areaBlocks.isEmpty()) return false;
        String before = sentenceBefore(text, at);

        int[] area = null;
        for (Block b : areaBlocks) {
            Pattern words = AREA_WORDS.get(b);
            int[] hit = words == null ? null : lastMatch(words, before);
            if (hit != null && (area == null || hit[0] > area[0])) area = hit;
        }
        if (area == null) return false;
        int[] page = lastMatch(PAGE_WORDS, before);
        if (page == null || area[0] > page[0]) return true;
        return page[0] - area[1] <= 2;
    }

    /** 색이 나온 문장에서 색 앞까지의 글 */
    private static String sentenceBefore(String text, int at) {
        int from = 0;
        Matcher m = SENTENCE_BREAK.matcher(text);
        while (m.find() && m.end() <= at) from = m.end();
        return text.substring(from, at);
    }

    /** 마지막으로 걸린 자리 [시작, 끝). 없으면 null */
    private static int[] lastMatch(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int[] last = null;
        while (m.find()) last = new int[]{m.start(), m.end()};
        return last;
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
