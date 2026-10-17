package com.newvent.registry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 페이지 색감 — 모델이 **고르기만** 하는 팔레트.
 *
 * ★ 어떻게 적용되나
 *   모델은 페이지 루트를 못 만진다(블록 단위로만 출력한다). 그래서
 *   hero 섹션의 class 에 palette-* 를 붙이게 하고, 저장 직전에 서버가
 *   그걸 루트(.ev-container)로 옮긴다 — {@link PageShell#hoistPalette}.
 *   hero 를 고르는 이유: 모든 페이지에 있고(필수), 페이지 인상을 정하는 자리라서다.
 *
 * ★ 팔레트는 CSS 변수(--ev-primary 등)만 바꾼다
 *   event.css 의 `.palette-*` 규칙. 테마(theme-*)와 같은 변수를 덮어쓰고,
 *   파일에서 테마보다 뒤에 있어서 둘 다 있으면 팔레트가 이긴다.
 *   템플릿 페이지는 95-template-accent.css 가 포인트색을 팔레트에 잇는다.
 *
 * ★ BASE 는 "원래대로" 다. 루트의 팔레트를 지우고 아무것도 붙이지 않는다.
 */
public enum Palette implements Pickable {

    // ★ 모델이 이 설명을 보고 고른다. "원래 색으로" 한 가지만 적었을 때 "원래대로 되돌려줘" 에는
    //   다른 팔레트(brand · pastel)를 골랐다(실제 화면). 되돌리는 말투를 같이 적어 둔다.
    BASE("palette-base", "원래 색 · 원래대로 · 기본 색 · 처음 상태로 되돌린다, 색 초기화 (템플릿 테마 또는 기본 남색)", true, false),
    SPRING("palette-spring", "봄 — 벚꽃 분홍, 연두", true, false),
    SUMMER("palette-summer", "여름 — 바다 파랑, 청량한 하늘색", true, false),
    AUTUMN("palette-autumn", "가을 — 단풍 주황, 갈색", true, false),
    WINTER("palette-winter", "겨울 — 눈 내린 남색, 은빛", true, false),
    FOREST("palette-forest", "숲 · 친환경 — 초록", true, false),
    SUNSET("palette-sunset", "노을 — 코랄, 보라", true, false),
    NIGHT("palette-night", "밤 · 프리미엄 — 검정, 금색", true, true),
    PASTEL("palette-pastel", "파스텔 — 연보라, 민트", true, false),
    MONO("palette-mono", "모노 · 미니멀 — 검정, 회색", true, false),
    FESTIVE("palette-festive", "축제 · 명절 — 빨강, 금색", true, false),
    CHRISTMAS("palette-christmas", "크리스마스 — 진한 초록, 빨강", true, false),
    NEWYEAR("palette-newyear", "새해 — 남색, 금색", true, false),
    VALENTINE("palette-valentine", "발렌타인 — 분홍, 버건디", true, false),
    KIDS("palette-kids", "어린이 · 가족 — 밝은 원색", true, false),
    ESPORTS("palette-esports", "게임 · e스포츠 — 검정, 네온 초록", true, true),
    LAVENDER("palette-lavender", "라벤더 — 연보라, 차분한", true, false),
    COFFEE("palette-coffee", "카페 · 따뜻한 — 갈색, 크림", true, false),
    // ★ 설명에서 회사명을 뺐다. 이 문장이 그대로 프롬프트에 실리는데,
    //   PR 리뷰의 "회사명 NewVent 를 문구에 넣는다 (요청에 없음)" 이 여기서 샜을 가능성이 높다.
    BRAND("palette-brand", "브랜드 기본 — 마젠타 (특별한 분위기 요청이 없을 때)", true, false),

    // ── 확장 — 요청문이 부를 때만 프롬프트에 실린다 ─────────
    //   출처 · 라이선스: docs/registry-candidates.json. 색값은 event.css 90-palettes/03-expansion.css
    RETRO("palette-retro", "레트로 — 크림, 바랜 빨강, 겨자", false, false),
    LEMONADE("palette-lemonade", "레모네이드 — 레몬 노랑, 연두", false, false),
    AQUA("palette-aqua", "아쿠아 — 청록, 물빛", false, false),
    EMERALD("palette-emerald", "에메랄드 — 에메랄드 초록, 보석", false, false),
    CUPCAKE("palette-cupcake", "컵케이크 — 분홍, 민트, 크림", false, false),
    BUMBLEBEE("palette-bumblebee", "꿀벌 — 노랑, 검정 줄무늬", false, false),
    CORPORATE("palette-corporate", "기업 — 신뢰감 있는 파랑, 회색", false, false),
    GARDEN("palette-garden", "가든 — 꽃분홍, 잎 초록", false, false),
    FANTASY("palette-fantasy", "판타지 — 보라, 하늘, 몽환적", false, false),
    CMYK("palette-cmyk", "CMYK — 인쇄 원색 (청록, 자홍, 노랑)", false, false),
    ACID("palette-acid", "애시드 — 형광 연두, 자홍", false, false),
    WIREFRAME("palette-wireframe", "와이어프레임 — 흰 바탕, 검은 선, 회색", false, false),
    CARAMEL("palette-caramel", "카라멜 — 카라멜 갈색, 라떼 크림", false, false),
    SILK("palette-silk", "실크 — 아이보리, 은은한 베이지", false, false),
    ZINC("palette-zinc", "징크 — 중성 회색, 차분한", false, false),
    SLATE("palette-slate", "슬레이트 — 푸른 회색, 차가운 돌", false, false),
    STONE("palette-stone", "스톤 — 따뜻한 회색", false, false),
    ROSE("palette-rose", "로즈 — 장미 빨강 단색", false, false),
    VIOLET("palette-violet", "바이올렛 — 보라색 단색", false, false),
    ORANGE("palette-orange", "오렌지 — 주황색 단색", false, false),
    CHUSEOK("palette-chuseok", "추석 — 보름달 황금, 달빛 남색, 한가위", false, false),
    PEPERO("palette-pepero", "빼빼로데이 — 초콜릿 갈색, 딸기 분홍", false, false),
    RAINY("palette-rainy", "장마 — 회청색, 빗방울", false, false),
    CHERRY("palette-cherry", "벚꽃 — 진한 벚꽃 분홍, 꽃놀이", false, false),
    SUNEUNG("palette-suneung", "수능 응원 — 응원 주황, 남색, 합격", false, false),
    CHILDRENS_DAY("palette-childrens-day", "어린이날 — 하늘, 노랑, 초록, 풍선", false, false),
    EARLY_SPRING("palette-early-spring", "초봄 — 새싹 연두, 연노랑", false, false),
    MONSOON("palette-monsoon", "장마철 — 짙은 남청, 비구름", false, false),
    HEATWAVE("palette-heatwave", "폭염 — 이글거리는 다홍, 한여름", false, false),
    FIRST_SNOW("palette-first-snow", "첫눈 — 흰색, 은회색, 눈송이", false, false),
    FOLIAGE("palette-foliage", "단풍 — 진홍 단풍잎, 와인빛 가을", false, false),
    SEOLLAL("palette-seollal", "설날 — 색동 (분홍, 노랑, 초록, 파랑)", false, false),
    DANO("palette-dano", "단오 — 창포 초록, 붉은 부채", false, false),
    HANGUL_DAY("palette-hangul-day", "한글날 — 먹색, 한지 크림", false, false),
    PARENTS_DAY("palette-parents-day", "어버이날 — 카네이션 빨강, 잎 초록", false, false),
    TEACHERS_DAY("palette-teachers-day", "스승의 날 — 카네이션 분홍, 칠판 초록", false, false),
    WHITE_DAY("palette-white-day", "화이트데이 — 은빛 회청, 흰색, 하늘", false, false),
    GRADUATION("palette-graduation", "졸업 · 입학 — 꽃다발 분홍, 남색 학사모", false, false),
    WORLDCUP("palette-worldcup", "월드컵 응원 — 응원 빨강, 함성", false, false),
    BASEBALL("palette-baseball", "야구 시즌 — 잔디 초록, 흰 공, 빨강 실밥", false, false),
    EASTER("palette-easter", "부활절 — 달걀 파스텔 (연보라, 연노랑, 민트)", false, false),
    ST_PATRICK("palette-st-patrick", "성 패트릭 — 클로버 초록, 금", false, false),
    DIWALI("palette-diwali", "디왈리 — 등불 금색, 주황", false, false),
    Y2K("palette-y2k", "Y2K — 은빛, 핑크, 하늘, 레트로 디지털", false, false),
    VINTAGE("palette-vintage", "빈티지 — 바랜 크림, 올리브, 갈색", false, false),
    EARTH("palette-earth", "어스 — 흙 갈색, 올리브", false, false),
    OCEAN_DEEP("palette-ocean-deep", "깊은 바다 — 진청, 청록", false, false),
    CANDY("palette-candy", "캔디 — 사탕 분홍, 하늘, 노랑", false, false),
    MATCHA("palette-matcha", "말차 — 회녹색 말차, 크림", false, false),
    PEACH("palette-peach", "피치 — 복숭아, 코랄", false, false),
    BURGUNDY("palette-burgundy", "버건디 — 와인 빨강, 금", false, false),
    MONOCHROME_BLUE("palette-monochrome-blue", "파랑 단색 — 파란색 한 가지 명도 단계", false, false),
    SEPIA("palette-sepia", "세피아 — 바랜 사진 갈색 톤", false, false),
    HIGH_CONTRAST("palette-high-contrast", "고대비 — 검정 글자, 진한 파랑, 읽기 쉽게", false, false),
    COLORBLIND_SAFE("palette-colorblind-safe", "색약 배려 — 파랑, 주황 (빨강 · 초록 구분에 기대지 않음)", false, false),
    CATPPUCCIN_LATTE("palette-catppuccin-latte", "카푸치노 라테 — 밝은 파스텔 (라벤더, 분홍, 하늘)", false, false),
    NORD_AURORA("palette-nord-aurora", "노르드 오로라 — 회청 바탕에 오로라 (빨강, 노랑, 초록, 보라)", false, false),
    NORD("palette-nord", "노르드 — 차가운 회청색, 북극", false, false),
    SOLARIZED_LIGHT("palette-solarized-light", "솔라라이즈드 — 크림 바탕, 청록, 주황", false, false),
    ROSE_PINE_DAWN("palette-rose-pine-dawn", "로제 파인 새벽 — 밝은 장미 베이지, 소나무 청록", false, false),
    EVERFOREST("palette-everforest", "에버포레스트 — 부드러운 숲 초록, 이끼", false, false),
    TOMATO("palette-tomato", "토마토 — 토마토 빨강", false, false),
    PLUM("palette-plum", "자두 — 자두 보라", false, false),
    IRIS("palette-iris", "아이리스 — 붓꽃 남보라", false, false),
    JADE("palette-jade", "비취 — 비취 초록, 옥", false, false),
    GRASS("palette-grass", "풀 — 싱그러운 풀 초록", false, false),
    MINT("palette-mint", "민트 — 밝은 민트, 청량한", false, false),
    BRONZE("palette-bronze", "브론즈 — 청동, 녹청빛", false, false),
    GOLD("palette-gold", "골드 — 금색, 고급", false, false),
    TEAL("palette-teal", "틸 — 청록색", false, false),
    LIME("palette-lime", "라임 — 라임 연두", false, false),
    AMBER("palette-amber", "앰버 — 호박색, 노란 주황", false, false),
    SKY("palette-sky", "스카이 — 하늘색, 맑은", false, false),
    FUCHSIA("palette-fuchsia", "푸크시아 — 자홍, 핫핑크", false, false),
    M3_BASELINE("palette-m3-baseline", "M3 기본 — 차분한 보라 톤", false, false),
    DANCHEONG("palette-dancheong", "단청 — 녹청, 주홍, 황", false, false),
    OBANGSAEK("palette-obangsaek", "오방색 — 청, 적, 황, 백, 흑", false, false),
    HANBOK("palette-hanbok", "한복 — 쪽빛 남색, 연분홍, 노랑 저고리", false, false),
    CELADON("palette-celadon", "청자 — 비색 연녹, 은은한", false, false),
    BAEKJA("palette-baekja", "백자 — 백자 흰색, 청화 남색", false, false),
    JEJU("palette-jeju", "제주 — 현무암 회색, 귤 주황, 바다", false, false),
    BUSAN("palette-busan", "부산 바다 — 해운대 청록 바다, 모래", false, false),
    HANOK("palette-hanok", "한옥 — 나무 갈색, 기와 회색, 창호지", false, false),
    BOKNAL("palette-boknal", "복날 — 인삼 갈색, 삼계탕 크림, 한여름 보양", false, false),
    GWANGBOKJEOL("palette-gwangbokjeol", "광복절 — 태극 빨강, 파랑, 흰색", false, false),
    KIMJANG("palette-kimjang", "김장 — 배추 연두, 고춧가루 빨강", false, false),
    BACK_TO_SCHOOL("palette-back-to-school", "개학 — 공책 하늘, 연필 노랑", false, false),
    SUMMER_VACATION("palette-summer-vacation", "여름 휴가 — 수박 빨강, 초록, 바다", false, false),
    SINGLES_DAY("palette-singles-day", "광군제 11.11 — 빨강, 금, 대할인", false, false),
    CYBER_MONDAY("palette-cyber-monday", "사이버먼데이 — 전자 파랑, 형광 하늘", false, false),
    THANKSGIVING("palette-thanksgiving", "추수감사절 — 호박 주황, 갈색, 수확", false, false),
    CITRUS("palette-citrus", "시트러스 — 오렌지, 레몬, 라임", false, false),
    BERRY("palette-berry", "베리 — 블루베리, 라즈베리", false, false),
    LAGOON("palette-lagoon", "라군 — 깊은 산호초 청록, 모래", false, false),
    DESERT("palette-desert", "사막 — 모래 베이지, 마른 흙", false, false),
    TROPICAL("palette-tropical", "트로피컬 — 야자 초록, 망고 노랑, 핑크", false, false),
    NORDIC("palette-nordic", "북유럽 — 흰 나무, 연회색, 짙은 초록", false, false),
    TERRACOTTA("palette-terracotta", "테라코타 — 구운 흙 주황, 크림", false, false),
    LAVENDER_FOG("palette-lavender-fog", "라벤더 안개 — 회보라, 안개 흰색", false, false),
    MOCHA_MOUSSE("palette-mocha-mousse", "모카 무스 — 모카 갈색, 우유", false, false),
    ELECTRIC("palette-electric", "일렉트릭 — 전기 파랑, 형광 노랑", false, false),
    SORBET("palette-sorbet", "셔벗 — 딸기, 멜론, 레몬 셔벗", false, false),
    INK("palette-ink", "먹 — 먹 검정, 붉은 낙관", false, false),
    CYBERPUNK("palette-cyberpunk", "사이버펑크 — 어두운 밤, 형광 노랑, 분홍", false, true),
    SYNTHWAVE("palette-synthwave", "신스웨이브 — 어두운 보라, 네온 분홍", false, true),
    HALLOWEEN("palette-halloween", "핼러윈 — 검정, 호박 주황, 보라", false, true),
    DRACULA("palette-dracula", "드라큘라 — 어두운 진보라, 분홍, 연두", false, true),
    LUXURY("palette-luxury", "럭셔리 — 검정, 샴페인 금", false, true),
    BUSINESS("palette-business", "비즈니스 — 어두운 남색, 차분한 파랑", false, true),
    DIM("palette-dim", "딤 — 어둡지만 부드러운 회청, 은은한 빛", false, true),
    ABYSS("palette-abyss", "심해 — 심해 남청색, 발광 청록", false, true),
    BLACKFRIDAY("palette-blackfriday", "블랙프라이데이 — 검정, 형광 빨강, 대할인", false, true),
    BLACK_DAY("palette-black-day", "블랙데이 — 검정, 짜장 갈색", false, true),
    NEON_NIGHT("palette-neon-night", "네온 나이트 — 검정, 형광 분홍, 형광 파랑", false, true),
    SAKURA_NIGHT("palette-sakura-night", "밤벚꽃 — 남색 밤하늘, 벚꽃 분홍", false, true),
    CATPPUCCIN_MOCHA("palette-catppuccin-mocha", "카푸치노 모카 — 어두운 바탕에 파스텔 (라벤더, 분홍)", false, true),
    SOLARIZED_DARK("palette-solarized-dark", "솔라라이즈드 어둡게 — 짙은 청록 바탕, 노랑", false, true),
    GRUVBOX("palette-gruvbox", "그루브박스 — 어두운 레트로 갈색, 주황, 노랑", false, true),
    ROSE_PINE("palette-rose-pine", "로제 파인 — 어두운 보라 바탕, 먼지 낀 장미", false, true),
    TOKYO_NIGHT("palette-tokyo-night", "도쿄 나이트 — 도시 밤 남색, 네온 파랑", false, true),
    KANAGAWA("palette-kanagawa", "가나가와 — 먹빛 남색 바탕, 크림, 파도", false, true),
    ONE_DARK("palette-one-dark", "원 다크 — 어두운 회청, 선명한 파랑", false, true),
    SEOUL_NIGHT("palette-seoul-night", "서울 야경 — 남산 야경 남색, 불빛 노랑", false, true),
    DAEBOREUM("palette-daeboreum", "정월대보름 — 밤하늘, 보름달 노랑", false, true),
    CHILSEOK("palette-chilseok", "칠석 — 은하수 남색, 별빛", false, true);

    public static final String PREFIX = "palette-";

    /** 요청문이 부른 확장 팔레트를 몇 개까지 더 싣나 */
    static final int MAX_MATCHED = 6;

    private static final Set<String> NOT_KEYWORDS = Set.of(
            "기본", "밝게", "어둡게", "밝은", "어두운", "진한", "연한", "짙은", "은은한", "차분한", "따뜻한",
            "부드러운", "선명한", "맑은", "고급", "단색", "바탕", "톤", "한", "가지", "명도", "단계",
            "흰색", "검정", "빨강", "파랑", "노랑", "초록", "보라", "분홍", "주황", "갈색", "회색", "금색", "금",
            "남색", "하늘", "크림", "청록", "자홍");

    private final String cssClass;
    private final String desc;
    private final boolean core;
    private final boolean dark;

    Palette(String cssClass, String desc, boolean core, boolean dark) {
        this.cssClass = cssClass;
        this.desc = desc;
        this.core = core;
        this.dark = dark;
    }

    @Override public String cssClass() { return cssClass; }
    @Override public String desc()     { return desc; }
    /** 프롬프트에 늘 실리는 기본 팔레트인가 */
    @Override public boolean core()    { return core; }
    /** 바탕이 어두운 팔레트인가 (event.css 99-dark-fixes 의 목록과 짝) */
    public boolean dark()    { return dark; }

    public static Optional<Palette> find(String cssClass) {
        if (cssClass == null) return Optional.empty();
        return Arrays.stream(values()).filter(p -> p.cssClass.equals(cssClass)).findFirst();
    }

    public static boolean looksLike(String cssClass) {
        return cssClass != null && cssClass.startsWith(PREFIX);
    }

    /** 전부 — 고른 값을 지키는 쪽(검증 · 찾기)이 쓴다. 프롬프트에는 {@link #forPrompt} */
    public static List<Palette> all() {
        return List.of(values());
    }

    public static List<Palette> basics() {
        return Arrays.stream(values()).filter(p -> p.core).toList();
    }

    public static List<Palette> forPrompt(String requestText) {
        return Stream.concat(basics().stream(), matched(requestText).stream()).toList();
    }

    static List<Palette> matched(String requestText) {
        return KeywordMatch.matched(all(), requestText, MAX_MATCHED, NOT_KEYWORDS, true);
    }
}
