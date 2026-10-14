package com.newvent.registry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 페이지 배치 — 영역들을 페이지 안에서 어떻게 놓을지. 모델이 <b>고르기만</b> 하는 class 목록.
 *
 * ★ 테마 · 팔레트 · 배치는 층이 다르다
 *   테마({@link Theme})는 꼴(배경 · 카드 · 타이포), 팔레트({@link Palette})는 색,
 *   배치는 <b>영역이 놓이는 자리</b>다. 겹치지 않으므로 그대로 조합이 된다.
 *   영역 하나의 안쪽 모양은 {@link Variant} 가 맡는다 — 여기는 영역끼리의 자리만 정한다.
 *
 * ★ 모델은 hero 의 class 에 붙여 고른다
 *   모델은 블록 단위로만 출력해서 페이지 루트를 못 만진다. 저장 직전 서버가 루트(.ev-container)로
 *   옮긴다 — {@link PageShell#hoistPalette} 와 같은 길이다.
 *
 * ★ 기본(STACK)은 class 를 남기지 않는다
 *   palette-base 와 같다. 고르면 루트의 배치 class 를 지운다. 그래서 배치를 고른 적 없는 옛 페이지 ·
 *   템플릿 페이지의 저장본은 글자 하나 달라지지 않는다.
 *
 * ★ 조건은 서버가 지킨다 — {@link #minContentBlocks}
 *   영역이 모자라면 격자가 빈다. 프롬프트에 "○개 미만이면 고르지 마라" 라고 적지 않는다 —
 *   금지문에서 이름을 배워 고른 전례가 있다({@link Variant} 의 NEEDS_ITEMS 주석).
 *   모델이 골라도 저장 직전에 떨어진다.
 *
 * ★ 템플릿 페이지에는 걸지 않는다
 *   템플릿 블록(.ev-block)은 자기 디자인이 있다. 배치를 얹으면 둘 다 망가진다 (테마와 같은 이유).
 *
 * ★ 이 파일과 event.css 의 `.layout-*` 규칙(src/event-css/80-layouts)은 짝이다.
 *   칸 크기는 CSS 가 <b>영역 종류</b>로 정한다. 모델이 칸 크기를 고르지 않는다.
 */
public enum Layout {
    STACK("layout-stack", "세로 한 줄 — 기본. 영역이 적거나 차분하게 읽히는 페이지", 0,
            "(배치|레이아웃).{0,10}(원래|기본(?! ?(색|컬러|톤|테마|글|폰트))|세로 ?한 ?줄|한 ?줄)|(원래|기본) ?(배치|레이아웃)|세로 ?한 ?줄(로|으로)? ?(배치|쌓|바꿔|돌려)"),
    BENTO("layout-bento", "벤토 — 영역을 크기가 다른 칸으로 나란히 놓는다. 내용이 많은 페이지", 4,
            "(?i)(벤토|bento).{0,12}(배치|레이아웃|layout)|(페이지|전체 ?배치|영역들).{0,12}(벤토|bento)"),
    SPLIT("layout-split", "좌우 분할 — 첫 화면을 좌우로 나눈다. 왼쪽에 제목, 오른쪽에 첫 영역. 나머지는 아래로 이어진다", 2,
            "좌우 ?(로 |으로 )?(분할|나눠|나누|나뉘)|분할 ?(화면|배치|레이아웃)|"
            + "(제목|타이틀).{0,14}왼쪽.{0,30}(나머지|내용|본문).{0,14}오른쪽"),
    WIDE("layout-wide", "넓은 2열 — 영역을 두 줄로 나란히 놓는다", 2,
            "(페이지|전체|영역들|영역을|영역들을).{0,16}(2|두) ?(열|단)(?!계)|(2|두) ?(열|단)(?!계)(로|으로)? ?.{0,8}(배치|레이아웃)"),
    THREE("layout-three", "3열 — 영역을 세 줄로 나란히 놓는다. 짧은 영역이 여럿인 페이지", 3,
            "(페이지|전체|영역들|영역을|영역들을).{0,16}(3|세|삼) ?(열|단)(?!계)|(3|세|삼) ?(열|단)(?!계)(로|으로)? ?.{0,8}(배치|레이아웃)"),
    MAGAZINE("layout-magazine", "매거진 — 첫 내용 영역을 크게 한 줄로 놓고, 나머지는 두 줄로 나란히 놓는다. 대표 내용 하나가 뚜렷한 페이지", 3,
            "(?i)(매거진|잡지|magazine).{0,12}(배치|레이아웃|layout|형식|스타일)|(페이지|전체 ?배치|영역들).{0,12}(매거진|잡지|magazine)"
            + "|(매거진|잡지)(처럼| ?느낌| ?풍)"),
    ZIGZAG("layout-zigzag", "지그재그 — 영역을 좌우로 번갈아 치우쳐 놓는다. 위에서 아래로 리듬 있게 읽히는 페이지", 2,
            "(?i)(지그재그|zigzag|엇갈리게|엇갈려|번갈아).{0,12}(배치|레이아웃|layout)|(페이지|전체 ?배치|영역들|영역을).{0,12}(지그재그|zigzag|엇갈리게|엇갈려|번갈아)"),
    FULL("layout-full", "풀블리드 — 영역마다 화면 폭을 끝까지 쓰는 띠로 놓는다. 랜딩 페이지처럼 시원하게 펼쳐지는 페이지", 0,
            "(?i)풀 ?블리드|full ?-?bleed|(띠|밴드) ?(형|배치|레이아웃)|랜딩 ?페이지 ?(처럼|형|스타일|느낌)|전체 ?폭 ?(배치|레이아웃)"
            + "|(페이지|전체|영역들을?|영역을).{0,10}(꽉|끝까지|가득) ?(채|차|쓰)"),
    FOCUS("layout-focus", "포커스 — 폭을 좁혀 가운데 한 줄로 놓고 여백을 넓게 둔다. 글처럼 차분하게 읽히는 페이지", 0,
            "(?i)포커스 ?(배치|레이아웃|형)|focus ?(layout|배치|레이아웃)"
            + "|(페이지|전체|영역들을?|영역을).{0,10}(폭|너비)(을|를)? ?.{0,4}(좁게|좁혀|줄여)"
            + "|(좁은|좁게) ?(한 ?줄|폭) ?.{0,6}(배치|레이아웃)");

    public static final String PREFIX = "layout-";

    /** 모델이 안 고르거나 지어냈을 때 — class 를 남기지 않는 것과 같다 */
    public static final Layout DEFAULT = STACK;

    // 영역을 가리키는 말 - 열쇠말의 목적어 자리(바로 앞)나 열쇠말 안에 있으면 그 영역의 모양 요청이다.
    private static final String REGION_WORDS =
            "혜택|참여 ?방법|참여 ?대상|대상자|경품|쿠폰|일정|faq|자주 ?묻는 ?질문|질문|비교 ?표|비교|소개|숫자|통계|유의 ?사항"
            + "|버튼|카드|항목|목록|리스트";

    private static final Pattern REGION = Pattern.compile("(?i)" + REGION_WORDS);

    // 목적어 자리 - 영역 이름 (+ 영역 · 카드 같은 꼬리말) (+ 조사) (+ "넓게" 같은 꾸밈말 하나) 로 끝난다.
    private static final Pattern REGION_OBJECT = Pattern.compile(
            "(?i)(" + REGION_WORDS + ")( ?(영역|부분|섹션|쪽|카드|항목|목록))?(을|를|은|는|이|가|의|도|만)?\s*"
            + "([가-힣]+(게|히)\s+)?$");

    private final String cssClass;
    private final String desc;
    private final int minContentBlocks;
    private final Pattern trigger;

    Layout(String cssClass, String desc, int minContentBlocks, String trigger) {
        this.cssClass = cssClass;
        this.desc = desc;
        this.minContentBlocks = minContentBlocks;
        this.trigger = Pattern.compile(trigger);
    }

    /**
     * 요청문이 페이지 배치를 <b>직접</b> 말했으면 그 배치. 아니면 빈 값.
    */
    public static Optional<Layout> requestedBy(String requestText) {
        if (requestText == null || requestText.isBlank()) return Optional.empty();
        return Arrays.stream(values()).filter(l -> l.saysPageLayout(requestText)).findFirst();
    }

    /** 열쇠말이 걸린 자리 중 하나라도 영역을 가리키지 않으면 페이지 배치다 */
    private boolean saysPageLayout(String text) {
        Matcher m = trigger.matcher(text);
        while (m.find()) {
            if (!aimsAtRegion(text, m.start(), m.end())) return true;
        }
        return false;
    }

    /**
     * 이 열쇠말이 영역 하나를 두고 한 말인가.
     *   열쇠말 안에 영역 이름이 있다       "페이지의 혜택을 2열로"
     *   열쇠말의 목적어가 영역이다         "혜택 영역을 벤토 배치로", "FAQ 레이아웃을 기본으로"
     */
    private static boolean aimsAtRegion(String text, int start, int end) {
        return REGION.matcher(text.substring(start, end)).find()
                || REGION_OBJECT.matcher(text.substring(0, start)).find();
    }

    // ★ 배치를 말하는 문장을 이루는 말 — 이것만 남았으면 배치 말고 시킨 게 없다
    private static final Pattern FILLER = Pattern.compile(
            "(?i)페이지|전체|화면|영역들?|배치|레이아웃|layout|그리드|grid|형태|모양|방식|스타일|느낌|구조|원래대로|기본"
            + "|좀|더|다시|한번|그냥|이제|지금|이걸|이거|그리고"
            + "|대|세|줄|한|두|삼|열|단|세로|가로"             // 열쇠말을 걷어 낸 뒤 남는 조각 ("원래[대로]", "[세]로 [한] [줄]로")
            + "|.*(게|히)"                                // 넓게 · 깔끔하게 · 나란히 — 꾸밈말은 시키는 일이 아니다
            + "|.*(줘|주세요|줄래|주라|해|하고|바꾸|바꾸고|바꿔|돌려|돌리고|만들어|만들고|나눠|나누고)");   // 해줘 · 바꿔줘 · 바꾸고

    private static final Pattern PARTICLE = Pattern.compile("(으로|로|을|를|은|는|이|가|도|만|의|에)$");

    private static final Pattern CLAUSE = Pattern.compile("[,.;!?\\n]|(?<=[가-힣]고)\\s|그리고|하면서|면서");

    /**
     * 배치 말고 <b>hero 가 할 일</b>이 요청문에 더 있는가 - "전체 색감을 가을로 바꾸고 페이지를 벤토 배치로".
    */
    public static boolean asksMoreOfHero(String requestText) {
        if (requestText == null || requestText.isBlank()) return false;
        String rest = requestText;
        for (Layout l : values()) rest = l.trigger.matcher(rest).replaceAll(" ");
        for (String clause : CLAUSE.split(rest)) {
            if (REGION.matcher(clause).find()) continue;
            for (String word : clause.trim().split("\\s+")) {
                String stem = PARTICLE.matcher(word.replaceAll("[\\p{Punct}\\s'\"‘’“”]", "")).replaceFirst("");
                if (!stem.isEmpty() && !FILLER.matcher(stem).matches()) return true;
            }
        }
        return false;
    }

    /**
     * 요청문에서 <b>배치를 말한 마디</b>를 뺀 나머지 - 배치는 서버가 걸고, 모델에게는 나머지만 보여 준다.
     *   "전체 색감을 가을 느낌으로 바꾸고 매거진 레이아웃으로 바꿔줘" → "전체 색감을 가을 느낌으로 바꾸고"
    */
    public static String withoutLayoutTalk(String requestText) {
        if (requestText == null || requestText.isBlank()) return requestText;
        // 마디의 경계 — [시작, 끝)
        java.util.List<int[]> clauses = new java.util.ArrayList<>();
        Matcher brk = CLAUSE.matcher(requestText);
        int from = 0;
        while (brk.find()) {
            clauses.add(new int[]{from, brk.end()});
            from = brk.end();
        }
        clauses.add(new int[]{from, requestText.length()});

        boolean[] drop = new boolean[clauses.size()];
        for (Layout l : values()) {
            Matcher m = l.trigger.matcher(requestText);
            while (m.find()) {
                if (aimsAtRegion(requestText, m.start(), m.end())) continue;   // 영역 얘기는 모델 몫이다
                for (int i = 0; i < clauses.size(); i++) {
                    int[] c = clauses.get(i);
                    if (m.start() < c[1] && m.end() > c[0]) drop[i] = true;
                }
            }
        }
        StringBuilder rest = new StringBuilder();
        for (int i = 0; i < clauses.size(); i++) {
            if (!drop[i]) rest.append(requestText, clauses.get(i)[0], clauses.get(i)[1]);
        }
        String out = rest.toString().strip();
        return out.isEmpty() ? requestText : out;
    }

    /** 요청문이 "페이지" 를 콕 집었는가 — 영역을 고른 채 온 배치 요청을 가려낼 때 쓴다 */
    public static boolean saysPage(String requestText) {
        return requestText != null && requestText.contains("페이지");
    }

    public String cssClass() { return cssClass; }
    public String desc()     { return desc; }

    /**
     * 이 배치가 말이 되려면 있어야 하는 내용 영역 수 — hero · notices · cta 를 뺀 영역.
     * 모자라면 {@link PageShell} 이 기본 배치로 되돌린다.
     */
    public int minContentBlocks() { return minContentBlocks; }

    public static Optional<Layout> find(String cssClass) {
        if (cssClass == null) return Optional.empty();
        return Arrays.stream(values()).filter(l -> l.cssClass.equals(cssClass)).findFirst();
    }

    public static boolean looksLike(String cssClass) {
        return cssClass != null && cssClass.startsWith(PREFIX);
    }

    public static List<Layout> all() {
        return List.of(values());
    }
}
