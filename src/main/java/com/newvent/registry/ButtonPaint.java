package com.newvent.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 채팅 수정의 "참여 버튼을 검정색으로" 를 서버가 버튼에 직접 칠한다.
 *
 * ★ 모델에게 맡기지 않는 이유 — 수정 프롬프트에는 버튼 색을 바꿀 수단이 없다.
 *   팔레트는 페이지 전체라 영역을 고르면 막히고, 변형은 모양이다. 모델은 손에 잡히는
 *   v-cta-dark("어두운 상자 안의 밝은 버튼")를 골라 상자를 검게, 버튼을 희게 만들었다.
 *   "배경 말고 버튼 자체를" 로 다시 말하면 고를 게 없어 실패했다 (실측).
 *
 * ★ background-image: none 을 같이 박는다 — CTA 버튼 대부분이 그라데이션(--ev-gradient-cta)이라
 *   background-color 만 넣으면 그라데이션이 그 위를 덮어 색이 안 보인다.
 */
public final class ButtonPaint {

    private static final Map<String, String> HEX = Map.ofEntries(
            Map.entry("빨강", "#e03131"), Map.entry("주황", "#f76707"), Map.entry("노랑", "#fcc419"),
            Map.entry("연두", "#94d82d"), Map.entry("초록", "#2f9e44"), Map.entry("청록", "#0ca678"),
            Map.entry("하늘색", "#4dabf7"), Map.entry("파랑", "#1c7ed6"), Map.entry("남색", "#1e2a5a"),
            Map.entry("보라", "#7048e8"), Map.entry("분홍", "#f06595"), Map.entry("마젠타", "#d6336c"),
            Map.entry("코랄", "#ff6b6b"), Map.entry("버건디", "#7b2342"), Map.entry("민트", "#63e6be"),
            Map.entry("갈색", "#8b5a3c"), Map.entry("크림", "#f3e9d2"), Map.entry("살구", "#ffa98a"),
            Map.entry("금색", "#c9a227"), Map.entry("은색", "#ced4da"), Map.entry("검정", "#111111"),
            Map.entry("회색", "#868e96"), Map.entry("하양", "#ffffff"));

    /** 버튼이 아니라 영역 배경을 말하는 말. "버튼" 이 같이 있으면 버튼으로 본다("배경 말고 버튼 자체를") */
    private static final Pattern BACKGROUND_TALK = Pattern.compile("배경|바탕|상자|박스|영역|전체");
    private static final Pattern TEXT_TALK = Pattern.compile("글자|글씨|텍스트");

    /** 색칠 요청에 흔히 붙는 말. 이것과 색 이름을 다 빼고도 남는 말이 있으면 모델이 할 일이 더 있다 */
    private static final Pattern PAINT_TALK = Pattern.compile(
            "참여|버튼|자체|배경|바탕|상자|박스|글자|글씨|텍스트|색깔|색상|컬러|색|진하게|진한|연하게|연한"
            // 직전 요청을 가리키는 말 — "참여 버튼도 그렇게 바꿔줘" 는 색칠만 시킨 것이다
            + "|그렇게|그런 ?식으로|똑같이|마찬가지로|동일하게"
            + "|아니라|아니고|말고|바꿔|바꾸|변경|칠해|해주세요|해줘|주세요|해|줘|좀|더|만|그냥|요"
            + "|으로|로|을|를|이|가|은|는|의|도|[^\\p{L}\\p{N}]");

    private static final String DARK_BOX = "v-cta-dark";

    private final String colorName;
    private final String hex;
    private final boolean textOnly;

    private ButtonPaint(String colorName, String hex, boolean textOnly) {
        this.colorName = colorName;
        this.hex = hex;
        this.textOnly = textOnly;
    }

    /** 참여 버튼 색을 칠하라는 요청이면 칠할 색. 영역 배경 · 페이지 전체 색 이야기면 빈 값 */
    public static Optional<ButtonPaint> requestedBy(String requestText) {
        if (requestText == null || requestText.isBlank()) return Optional.empty();
        if (!requestText.contains("버튼") && BACKGROUND_TALK.matcher(requestText).find()) return Optional.empty();

        // 여러 색이 나오면 마지막 것 — "파란 버튼을 빨간색으로" 의 목표는 뒤에 온다
        ColorWords.Group picked = null;
        int at = -1;
        for (ColorWords.Group g : ColorWords.GROUPS) {
            for (String w : g.words()) {
                int i = requestText.lastIndexOf(w);
                if (i > at) {
                    at = i;
                    picked = g;
                }
            }
        }
        if (picked == null) return Optional.empty();
        return Optional.of(new ButtonPaint(picked.name(), HEX.get(picked.name()),
                TEXT_TALK.matcher(requestText).find()));
    }

    /** 색칠 말고는 시킨 게 없는가 — 그렇다면 모델을 부르지 않는다 */
    public static boolean onlyPaint(String requestText) {
        String rest = requestText == null ? "" : requestText;
        for (ColorWords.Group g : ColorWords.GROUPS) {
            for (String w : g.words()) rest = rest.replace(w, "");
        }
        return PAINT_TALK.matcher(rest).replaceAll("").length() < 2;
    }

    /** 칠할 버튼이 이 문서에 있는가 */
    public boolean fits(String html) {
        return button(Jsoup.parseBodyFragment(html)) != null;
    }

    /** 참여 버튼에 색을 인라인으로 칠한다. 칠할 버튼이 없으면 그대로 돌려준다 */
    public String apply(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        Element button = button(doc);
        if (button == null) return html;

        Map<String, String> style = parseStyle(button.attr("style"));
        if (textOnly) {
            style.put("color", hex);
        } else {
            style.put("background-color", hex);
            style.put("background-image", "none");
            style.put("color", luminance(hex) > 0.5 ? "#111111" : "#ffffff");
            // ★ 어두운 버튼을 어두운 상자에 두면 버튼이 사라진다. 그 상자는 "밝은 버튼" 을 위한 변형이다
            Element section = doc.body().selectFirst(Block.CTA.selector());
            if (section != null && luminance(hex) < 0.06) section.removeClass(DARK_BOX);
        }
        button.attr("style", serializeStyle(style));
        return doc.body().html();
    }

    public String summary() {
        return "참여 버튼 " + (textOnly ? "글자색" : "색") + "을 " + colorName + "으로 변경";
    }

    String hex() {
        return hex;
    }

    boolean textOnly() {
        return textOnly;
    }

    private static Element button(Document doc) {
        Element section = doc.body().selectFirst(Block.CTA.selector());
        if (section == null) return null;
        Element slot = section.selectFirst("[data-slot=cta-link]");
        if (slot != null) return slot;
        Element labeled = section.selectFirst("[data-behavior~=participate]");
        return labeled != null ? labeled : section.selectFirst("a, button, .btn");
    }

    private static Map<String, String> parseStyle(String style) {
        Map<String, String> out = new LinkedHashMap<>();
        if (style == null) return out;
        for (String decl : style.split(";")) {
            int i = decl.indexOf(':');
            if (i > 0) out.put(decl.substring(0, i).trim().toLowerCase(), decl.substring(i + 1).trim());
        }
        return out;
    }

    private static String serializeStyle(Map<String, String> style) {
        StringBuilder s = new StringBuilder();
        style.forEach((k, v) -> s.append(k).append(':').append(v).append(';'));
        return s.toString();
    }

    /** sRGB 상대 휘도 0(검정) ~ 1(흰색) */
    private static double luminance(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }
}
