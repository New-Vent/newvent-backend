package com.newvent.registry;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jsoup.nodes.Element;

/**
 * 관리자가 올린 이미지의 **배치 이름** — 모델이 고르기만 하는 class 목록.
 *
 * ★ 왜 이름을 고르게 하나
 *   모델은 이미지를 볼 수 없다. LlmClient.Request 는 문자열 둘이고 BedrockClient 는
 *   ContentBlock.fromText 만 보낸다. 그런데 **배치는 보는 일이 아니라 이름 고르는 일**이다 —
 *   어디 둘지는 관리자가 말한다. 그래서 변형 · 팔레트 · 장식과 같은 길로 간다.
 *
 * ★ 자리 · 크기 · 잘림 · 덮개 · 글자색은 전부 CSS 가 쥔다 (95-assets/01-assets.css).
 *   모델이 틀릴 수 있는 건 "어느 블록에 어떤 이름" 둘뿐이고, 둘 다 관리자가 한마디 더 하면 고쳐진다.
 *
 * ★ src 는 여기서 안 본다. 허용 목록 대조는 BlockValidator 가 한다 —
 *   모델이 주소를 지어내면 <img> 를 통째로 지운다.
 */
public enum Asset {

    /** 영역 배경으로 꽉 채운다. 자기 배경이 있는 블록에만 */
    BG("ev-asset-bg", "영역 배경으로 꽉 채운다"),
    /** 제목 옆 그림. hero 전용 — 다른 블록에는 글자 칸을 나눌 구조가 없다 */
    ART("ev-asset-art", "제목 옆에 두는 그림"),
    /** 항목 아이콘. benefits · steps 의 54x54 자리(지금 이모지가 들어가는 곳) */
    ICON("ev-asset-icon", "항목 앞 아이콘"),
    /** 맨 위 로고. hero 전용 */
    LOGO("ev-asset-logo", "맨 위에 두는 로고"),
    /** 가로 띠 */
    BAND("ev-asset-band", "가로로 꽉 찬 띠"),
    /**
     * 페이지 전체 배경. ★ 영역이 아니라 **페이지**에 깔린다 —
     * 모델은 hero 안에 쓰고, 저장 직전 서버가 루트로 옮긴다(PageShell.hoistPageAsset).
     * 팔레트 · 테마가 가는 길과 같다.
     */
    PAGE("ev-asset-page", "페이지 전체 배경 (영역이 아니라 페이지 뒤에 깔린다)");

    /** 올린 이미지에 공통으로 붙는 이름. 이게 있어야 CSS 가 글자를 앞으로 끌어올린다 */
    public static final String BASE = "ev-asset";

    /**
     * 프롬프트에 싣는 이미지 한 장 — 주소와 설명.
     *
     * ★ 레지스트리가 asset 패키지를 모르게 하려고 여기 둔다. 반대 방향(asset → registry)만 둔다.
     * ★ 설명이 필요한 이유 — 모델은 그림을 못 본다. 두 장 이상일 때 어느 것을 쓸지 가르는 건
     *   관리자가 올리며 적은 설명뿐이다.
     */
    /**
     * 사진 위의 글자를 어떻게 할 것인가. 올릴 때 ImageProbe 가 한 번 재서 정한다 —
     * CSS 는 사진 색을 모르고 모델은 그림을 못 보므로, 바이트가 손에 있을 때뿐이다.
     *
     * ★ 여기(registry)에 둔 이유: asset 모듈이 registry 를 쓰지 그 반대가 아니다.
     */
    public enum Tone {
        /** 밝은 그림 — 어두운 글씨, 덮개 없음 */
        LIGHT("ev-asset-on-light"),
        /** 어두운 사진 — 흰 글씨, 덮개 없음 */
        DARK("ev-asset-on-dark"),
        /** 섞인 사진 — 흰 글씨 + 덮개. 어느 글자색도 양 끝을 못 만족시킨다 */
        MIXED("ev-asset-on-mixed");

        private final String cssClass;

        Tone(String cssClass) {
            this.cssClass = cssClass;
        }

        public String cssClass() {
            return cssClass;
        }
    }

    /**
     * @param scrim MIXED 일 때의 덮개 농도. LIGHT · DARK 는 0 이다 — 사진을 안 덮는다
     * @param tone  글자를 어떻게 할 것인가
     */
    public record Ref(String url, String caption, double scrim, Tone tone) {}

    /** 배치 이름의 공통 접두사. 정화에서 이걸로 알아본다 */
    public static final String PREFIX = "ev-asset-";

    /**
     * 역할이 아니라 **역할을 조금 바꾸는** 이름. 역할 목록에 없으므로 find() 로는 안 잡힌다.
     *
     * ★ 여기 안 적으면 sanitize 가 "지어낸 이름" 으로 보고 지운다 —
     *   붙인 다음 수정에서 조용히 사라진다(실측: art-left 가 그랬다).
     * ★ 글자색(on-*) · 덮개(dim-*)는 여기 없다. 그건 매번 paintable 이 다시 붙인다.
     */
    private static final Set<String> MODIFIERS = Set.of(PREFIX + "art-left");

    /**
     * 배경 · 띠를 쓸 수 있는 블록. **자기 배경이 색인 블록만이다.**
     * 흰 카드 위에 사진을 깔면 카드가 통째로 사라진다 — Variant.DECOR_BLOCKS 와 같은 이유다.
     */
    private static final Set<Block> SURFACE_BLOCKS =
            EnumSet.of(Block.HERO, Block.HIGHLIGHT, Block.CTA);

    /** 아이콘을 쓸 수 있는 블록 — 54x54 자리가 있는 곳 */
    private static final Set<Block> ICON_BLOCKS = EnumSet.of(Block.BENEFITS, Block.STEPS);

    private final String cssClass;
    private final String desc;

    Asset(String cssClass, String desc) {
        this.cssClass = cssClass;
        this.desc = desc;
    }

    public String cssClass() { return cssClass; }
    public String desc()     { return desc; }

    /** 그 블록에 쓸 수 있는 배치인가 */
    public boolean allowedOn(Block b) {
        return switch (this) {
            case BG, BAND -> SURFACE_BLOCKS.contains(b);
            // ★ hero 에만 쓰게 한다. 어차피 루트로 옮겨지므로 어디에 쓰든 결과는 같은데,
            //   여러 영역에 흩어지면 **어느 것이 이기는지** 모델도 우리도 모른다
            case PAGE -> b == Block.HERO;
            case ART, LOGO -> b == Block.HERO;
            case ICON -> ICON_BLOCKS.contains(b);
        };
    }

    /** 그 블록에 쓸 수 있는 배치 전부 — 프롬프트가 안내 문구에 쓴다 */
    public static List<Asset> of(Block b) {
        return Arrays.stream(values()).filter(a -> a.allowedOn(b)).toList();
    }

    public static Optional<Asset> find(String cssClass) {
        if (cssClass == null) return Optional.empty();
        return Arrays.stream(values()).filter(a -> a.cssClass.equals(cssClass)).findFirst();
    }

    /** 배치 이름처럼 생겼나 — 목록에 있든 없든 */
    /** 올라온 이미지들의 주소만. 정화기가 src 를 대조할 허용 목록이다 */
    public static Set<String> urls(List<Ref> assets) {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (Ref r : assets) out.add(r.url());
        return java.util.Collections.unmodifiableSet(out);
    }

    public static boolean looksLike(String cssClass) {
        return cssClass != null && cssClass.startsWith(PREFIX);
    }

    /**
     * 수정 프롬프트에 실을 안내. 올린 이미지가 없으면 빈 목록 — 한 줄도 안 나간다.
     *
     * ★ 주소를 **글자 그대로** 쓰라고 못 박는다. 지어내면 BlockValidator 가 &lt;img&gt; 째로 지운다.
     * ★ 그 블록에 쓸 수 있는 배치만 싣는다. hero 에 아이콘을 안내하면 모델이 그걸 고른다.
     */
    public static List<String> promptLines(Block b, List<Ref> assets) {
        List<Asset> usable = of(b);
        if (assets == null || assets.isEmpty() || usable.isEmpty()) return List.of();

        List<String> out = new java.util.ArrayList<>();
        out.add("올린 이미지: (관리자가 올려 둔 것이다. 주소를 글자 그대로 쓴다)");
        // ★ 번호를 붙인다. 관리자가 "2번 이미지를 배경으로" 라고 말할 수 있어야 하고,
        //   화면의 그림 목록도 같은 번호를 보여 준다 — 그 둘을 잇는 유일한 끈이다
        for (int i = 0; i < assets.size(); i++) {
            Ref r = assets.get(i);
            out.add("    " + (i + 1) + ") " + r.url()
                    + (r.caption() == null || r.caption().isBlank() ? "" : "  — " + r.caption()));
        }
        out.add("- 이미지를 넣어 달라면 이 영역 안에 이렇게 쓴다:");
        out.add("    <img class=\"" + BASE + " 배치이름\" src=\"위 주소\" alt=\"\">");
        out.add("  배치 이름은 아래 중 하나를 고른다. 한 영역에 같은 배치를 두 번 쓰지 마라.");
        for (Asset a : usable) {
            out.add("    " + a.cssClass() + " : " + a.desc());
        }
        out.add("- 주소를 지어내거나 바꾸지 마라. 위에 없는 주소는 서버가 지운다.");
        out.add("- 이미지를 빼 달라면 그 <img> 를 지운다.");
        return out;
    }

    /**
     * 블록 안의 &lt;img&gt; 배치 이름을 정리한다. **모델 출력에만 건다.**
     *
     * ★ ev-asset-* 는 v- 도 t- 도 아니라서 cleanLooks 를 **그냥 지나간다.**
     *   모델이 ev-asset-huge 를 지어내면 CSS 가 없어 아무 효과도 없는데 지워지지도 않고
     *   저장되고, 다음 수정에서 모델이 그걸 보고 따라 쓴다. 들어오는 자리에서 지운다.
     *
     * ★ 같은 배치를 한 블록에 둘 이상 두지 않는다 — 배경 두 장은 겹쳐서 아래 것이 안 보인다.
     */
    public static void sanitize(Element section, Block b, List<Ref> refs) {
        EnumSet<Asset> used = EnumSet.noneOf(Asset.class);
        // ★ "img[class]" 가 아니다 — class 가 아예 없는 이미지는 선택조차 안 돼서
        //   살아남고, 자리잡는 CSS 가 없으니 본문에 원본 크기로 박힌다
        for (Element img : section.select("img")) {
            boolean kept = false;
            for (String c : List.copyOf(img.classNames())) {
                if (!looksLike(c) || MODIFIERS.contains(c)) continue;
                Optional<Asset> a = find(c).filter(x -> x.allowedOn(b));
                if (a.isEmpty() || !used.add(a.get())) img.removeClass(c);
                else kept = true;
            }
            // ★ 쓸 수 있는 배치 이름이 하나도 안 남았으면 <img> 를 **지운다.**
            //   class 만 떼면 자리를 못 잡는 맨 이미지가 본문 사이에 원본 크기로 끼어든다.
            //   관리자가 다시 한마디 하면 제자리에 들어가니, 틀린 자리보다 없는 편이 낫다.
            if (!kept || !placed(img, section)) img.remove();
            else {
                img.addClass(BASE);
                paintable(img, refs);
            }
        }
    }

    /**
     * 이 이미지가 **자리를 잡았는가.**
     *
     * ★★ 아이콘만 자리를 탄다. CSS 가 크기를 주는 규칙이 `.benefit-icon > .ev-asset-icon`
     *   처럼 **자리 안에 있을 때만** 걸린다. 밖에 있으면 규칙이 하나도 안 걸려서
     *   원본 크기 그대로 박힌다 — 실제로 혜택 카드 하나가 사진 높이만큼 늘어나고
     *   옆칸이 텅 비었다.
     * ★ 빈 자리가 있으면 거기로 옮긴다. 없으면 false — 호출하는 쪽이 지운다.
     *   틀린 자리에 큼직하게 박히느니 없는 편이 낫다.
     * ★ 나머지 배치는 CSS 가 영역 기준으로 자리를 잡으므로 어디 있든 된다.
     */
    private static boolean placed(Element img, Element section) {
        if (!img.hasClass(ICON.cssClass())) return true;
        if (img.parent() != null && img.parent().is(".benefit-icon, .step-icon")) return true;

        Element holder = section.selectFirst(".benefit-icon:not(:has(img)), .step-icon:not(:has(img))");
        if (holder == null) return false;
        holder.empty();
        holder.appendChild(img);
        return true;
    }

    /**
     * 사진 위 글자를 어떻게 할지 class 로 박는다. **서버만 붙인다 — 모델은 모른다.**
     *
     * ★ style="--ev-asset-scrim:…" 이 아니라 class 인 이유: 정화기(jsoup Safelist)가
     *   style 속성을 통째로 떼기 때문이다. class 는 ev-asset-* 라서 cleanLooks 도 지나간다.
     * ★ 배경에만 쓴다. 그림 · 아이콘 · 로고 · 띠 위에는 글자가 안 올라간다.
     * ★ 올리기 전에 들어온 이미지는 tone 이 없다(null). 그때는 아무것도 안 붙이고
     *   CSS 기본값(테마 글자색 그대로)에 맡긴다 — 틀린 색을 박는 것보다 낫다.
     */
    private static void paintable(Element img, List<Ref> refs) {
        // ★ 글자가 올라가는 둘에만. 그림 · 아이콘 · 로고 · 띠 위에는 글자가 안 온다
        if (refs == null) return;
        if (!img.hasClass(BG.cssClass()) && !img.hasClass(PAGE.cssClass())) return;
        String src = img.attr("src");
        for (Ref r : refs) {
            if (!src.equals(r.url())) continue;
            if (r.tone() == null) return;
            img.addClass(r.tone().cssClass());
            // ★ 덮개는 MIXED 에만. 농도까지 class 로 — CSS 에 세 줄이 짝으로 있다
            if (r.tone() == Tone.MIXED) {
                int pct = (int) Math.round(r.scrim() * 100);
                img.addClass(PREFIX + "dim-" + (pct == 35 || pct == 68 ? pct : 55));
            }
            return;
        }
    }
}
