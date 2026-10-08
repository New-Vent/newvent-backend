package com.newvent.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;

/**
 * 레지스트리로 생성 결과를 검증한다.
 *
 * ★ 여기에 "h1 이 있나" 같은 걸 직접 쓰지 마세요.
 *   전부 Block.must() 에서 읽어옵니다. 블록을 추가하면 검증도 자동으로 따라옵니다.
 *
 * 벤치마크 v8.4 의 check_html() 을 옮긴 것입니다.
 * 실패 메시지는 재시도 프롬프트에 그대로 들어갑니다 —
 * "JSON 파싱 실패" 같은 문법 메시지는 모델이 못 고치고,
 * "hero 영역이 없습니다" 같은 의미 메시지라야 고칩니다.
 */
public class BlockValidator {

    /**
     * 검증 실패 한 건.
     *
     * @param kind    무슨 실패인가. 분류·심각도·대응은 FailureCode 가 안다
     * @param detail  어디서 났나 — 블록 key, 슬롯 key, id, 수치 등. 없으면 null
     * @param message 재시도 프롬프트에 그대로 들어가는 의미 메시지
     *
     * ★ 코드 문자열을 손으로 조립하지 않는다. Failure.of(FailureCode, …) 로만 만든다.
     */
    public record Failure(FailureCode kind, String detail, String message) {

        public Failure {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(message, "message");
        }

        public static Failure of(FailureCode kind, String message) {
            return new Failure(kind, null, message);
        }

        public static Failure of(FailureCode kind, String detail, String message) {
            return new Failure(kind, detail, message);
        }

        /**
         * 로그(llm_call_logs.fail_codes)와 테스트가 보는 문자열 — "lost_hero", "slot_lost_period", "no_html".
         * ★ 형식을 바꾸면 과거 로그와 집계가 끊긴다.
         */
        public String code() {
            return detail == null ? kind.key() : kind.key() + "_" + detail;
        }

        /** 경고는 기록하되 결과를 실패로 만들거나 재시도를 일으키지 않는다. */
        public boolean isWarning() {
            return kind.severity() == FailureCode.Severity.WARNING;
        }

        /** 결과를 실패로 만든다 — 재시도하거나 관리자에게 되묻는다 */
        public boolean isBlocking() {
            return kind.severity() == FailureCode.Severity.HARD;
        }
    }

    /** 모델 출력에서 HTML 만 뽑는다. 코드펜스가 45% 확률로 붙어서 온다. */
    public static String extract(String raw) {
        String t = raw.replace("```html", "").replace("```", "");
        int s = t.indexOf('<'), e = t.lastIndexOf('>');
        return (s >= 0 && e > s) ? t.substring(s, e + 1).trim() : "";
    }

    /** 대괄호 자리표시자 — "[이벤트명]" "[혜택 1]" 같은 것. 15자까지 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[가-힣A-Za-z0-9 _\\-]{1,15}\\]");

    /** 생성 결과 검증 — 필수 블록이 다 있고 형태가 맞는가 */
    public static List<Failure> validateGenerated(String html) {
        return validateGenerated(html, null);
    }

    /**
     * 생성 결과 검증 — 입력에 원래 있던 대괄호는 자리표시자로 보지 않는다.
     *
     * ★ 왜 — 이벤트명이 "[단독] 가을 이벤트" 면 모델이 그대로 제목에 옮겨 쓰고,
     *   그게 자리표시자로 걸려 **매번 첫 시도가 실패했다**(Bedrock 실측 3/3, 제목에서 대괄호를
     *   빼자 1회 통과). 관리자가 쓴 글자는 자리표시자가 아니다.
     *
     * @param sourceText 제목 · 요청문을 이은 것. 여기 그대로 있는 [..] 는 허용한다. null 이면 전부 본다
     */
    public static List<Failure> validateGenerated(String html, String sourceText) {
        List<Failure> f = new ArrayList<>();
        if (html == null || html.isBlank()) {
            f.add(Failure.of(FailureCode.NO_HTML, "HTML을 찾을 수 없습니다. <section> 으로 시작하는 HTML만 출력하세요."));
            return f;
        }
        Document doc = Jsoup.parseBodyFragment(html);

        if (doc.body().select("section").isEmpty()) {
            f.add(Failure.of(FailureCode.NO_SECTION, "<section> 태그가 없습니다."));
        }
        for (Element sec : doc.body().select("section")) {
            if (!sec.hasAttr("data-block")) {
                f.add(Failure.of(FailureCode.NO_DATA_BLOCK,
                        "모든 <section> 에 data-block=\"이름\" 속성이 있어야 합니다."));
                break;
            }
        }

        for (Block b : Block.llmBlocks()) {
            Element el = doc.body().selectFirst(b.selector());
            if (el == null) {
                if (b.required()) {
                    f.add(Failure.of(FailureCode.LOST_BLOCK, b.key(), b.key() + " 영역이 없습니다."));
                }
                continue;
            }
            // ★ 같은 블록이 두 번이면 실패다 — 병합 · 추출(BlockMerge)이 블록마다 정확히 하나를 전제한다.
            //   그대로 저장하면 그 블록은 이후 AI 수정이 전부 터진다.
            //   실측: highlight 를 띠 배너 + 주의 상자로 두 번 냈다. 새 코드 대신 extra_ 로 남긴다.
            int count = doc.body().select(b.selector()).size();
            if (count > 1) {
                f.add(Failure.of(FailureCode.EXTRA_BLOCK, b.key(),
                        b.key() + " 영역이 " + count + "개입니다. 영역마다 하나만 씁니다. 하나로 합치세요."));
            }
            checkShape(b, el, f, false);
        }

        // 서버 소유 블록을 모델이 만들었나
        for (Block b : Block.serverBlocks()) {
            if (doc.body().selectFirst(b.selector()) != null) {
                f.add(Failure.of(FailureCode.WROTE_SERVER_BLOCK, b.key(),
                        b.key() + " 영역은 만들면 안 됩니다. 서버가 채웁니다."));
            }
        }

        Matcher m = PLACEHOLDER.matcher(html);
        while (m.find()) {
            if (sourceText != null && sourceText.contains(m.group())) continue;
            f.add(Failure.of(FailureCode.PLACEHOLDER, "자리표시자가 남아 있습니다. 해당 문장을 빼세요."));
            break;
        }
        if (sourceText != null) f.addAll(inventedYears(doc.body().text(), sourceText));
        return f;
    }

    /** 날짜로 쓰인 연도 — "2024년", "2024.12.31", "2024-12" 꼴. "2000원" 같은 금액은 안 걸린다 */
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?=\\s*년|[.\\-/]\\s*\\d)");

    /**
     * 입력에 없는 연도를 지어냈는가.
     *
     * ★ 왜 — 요청문은 "12월 31일까지" 였는데 모델이 "2024년 12월 31일까지" 로 썼다(Bedrock 실측,
     *   이벤트는 2026년). 연도 하나 틀리면 쿠폰 · 응모 기한이 지난 날짜로 게시된다.
     *   날짜는 프롬프트로 "지어내지 마라" 를 이미 시키지만 그걸로 안 막혔다. 연도만은 기계로 잡을 수 있다.
     *
     * ★ 허용하는 연도 — 이벤트명 · 요청문 · 이벤트 기간(sourceText 에 들어 있다)에 나온 것만.
     *   월 · 일은 보지 않는다. "12월 31일" 처럼 연도 없는 날짜는 요청문 그대로일 가능성이 높고,
     *   숫자 전체 대조는 수정 경로의 ValueCheck 몫이다.
     */
    static List<Failure> inventedYears(String text, String sourceText) {
        List<Failure> f = new ArrayList<>();
        Matcher y = YEAR.matcher(text == null ? "" : text);
        while (y.find()) {
            String year = y.group(1);
            if (sourceText.contains(year)) continue;
            f.add(Failure.of(FailureCode.VALUE_ADDED, "year_" + year,
                    "요청문에 없는 연도(" + year + ")를 썼습니다. 연도를 빼고 월 · 일만 쓰거나, 요청문에 있는 연도만 쓰세요."));
            break;
        }
        return f;
    }

    /**
     * 요청문에 열쇠말이 없는 선택 블록을 지운다 — {@link Block#allowedFor}. **생성 결과에만 건다.**
     *
     * ★ 프롬프트에서 빼고 "만들지 마라" 까지 말해도 만드는 경우가 남는다. 마지막 거름망이다.
     *   지우는 블록은 전부 선택(required=false)이라 검증이 깨지지 않는다.
     */
    public static String dropUntriggered(String html, String requestText) {
        if (html == null || requestText == null) return html;
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        boolean dropped = false;
        for (Element sec : doc.body().select("section[data-block]")) {
            Block b = Block.find(sec.attr("data-block")).orElse(null);
            if (b != null && !b.allowedFor(requestText)) {
                sec.remove();
                dropped = true;
            }
        }
        return dropped ? doc.body().html() : html;
    }

    /**
     * 수정 결과 검증 — 그 블록만 왔는가, 그리고 건드리면 안 되는 게 그대로인가.
     *
     * ★ before 가 필요한 이유
     *   수정은 "원본 대비" 로만 판정할 수 있는 항목이 있습니다.
     *   원본에 있던 [data-slot] 이나 id 가 없어졌는지는 원본을 봐야 압니다.
     *
     * @param before 수정 전 그 블록의 HTML (서버가 갖고 있는 것)
     * @param html   모델이 돌려준 것 (sanitizeEdited 를 먼저 거친 것)
     */
    public static List<Failure> validateEdited(Block target, String before, String html) {
        List<Failure> f = new ArrayList<>();
        Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);

        Element el = doc.body().selectFirst(target.selector());
        if (el == null) {
            f.add(Failure.of(FailureCode.LOST_BLOCK, target.key(), target.key() + " 영역이 없습니다."));
            return f;
        }
        for (Block b : Block.values()) {
            if (b != target && doc.body().selectFirst(b.selector()) != null) {
                f.add(Failure.of(FailureCode.EXTRA_BLOCK, b.key(),
                        b.key() + " 영역은 만들지 마세요. 요청한 영역만 출력하세요."));
            }
        }
        checkShape(target, el, f, true);
        checkItemCount(target, before, el, f);
        checkPreserved(before, html, f);
        return f;
    }

    /**
     * 블록 하나가 제 모양을 갖췄나.
     *
     * ★ 생성·수정이 같은 규칙을 쓰게 하는 지점입니다.
     *   두 곳에 따로 적으면 어긋납니다 — 실제로 minItems 가 수정 쪽에만 빠져 있었고,
     *   그래서 "생성으로는 못 만드는 상태를 수정으로는 만들 수 있는" 구멍이 있었습니다.
     *
     * ★ 메시지는 생성과 수정이 다릅니다.
     *   shape 는 **백지 생성**에서 시키는 말입니다("<ul> 안에 <li> 로").
     *   템플릿 블록을 수정하다 걸렸을 때 그 문장을 주면
     *   모델이 .benefit-card 구조를 <ul><li> 로 갈아엎습니다. 디자인이 망가집니다.
     */
    private static void checkShape(Block b, Element el, List<Failure> f, boolean editing) {
        if (b.must() == null) return;

        if (el.selectFirst(b.must()) == null) {
            f.add(Failure.of(FailureCode.EMPTY_BLOCK, b.key(), editing
                    ? b.key() + " 안에 내용이 없습니다. 원래 구조를 그대로 두고 문구만 고치세요."
                    : b.key() + " 안에 " + b.shape() + " — 맨 텍스트만 두면 안 됩니다."));
            return;
        }
        if (b.minItems() > 0) {
            // ★ li 를 하드코딩하지 않는다. must() 가 곧 세는 기준이다.
            //   must 가 "ul li, .benefit-card" 라 백지도 템플릿도 같은 코드로 센다.
            int n = el.select(b.must()).size();
            if (n < b.minItems()) {
                f.add(Failure.of(FailureCode.FEW_ITEMS, b.key(),
                        b.key() + " 항목이 " + n + "개입니다. " + b.minItems() + "개 이상 필요합니다."));
            }
        }
    }

    /**
	 * ★ 늘어난 것만 본다. 줄어든 것은 허용한다.
	 *   "복주머니를 2개만 보여줘" 는 정상 요청이다 (TemplateRoundTripTest).
	 *   전멸은 minItems(few_키)가 잡는다.
     *
     *  ★ container() 가 null 인 블록(hero·notices·cta)은 검사하지 않는다.
     */
    private static void checkItemCount(Block target, String before, Element el, List<Failure> f) {
    	if (target.container() == null) return;

    	Document beforeDoc = Jsoup.parseBodyFragment(before == null ? "" : before);
    	Element beforeBox = beforeDoc.body().selectFirst(target.container());
    	Element afterBox = el.selectFirst(target.container());
    	if (beforeBox == null || afterBox == null) return;				// 없으면 shape 쪽에서 잡음

    	int wasMust = beforeBox.select(target.must()).size();
    	int nowMust = afterBox.select(target.must()).size();
    	int wasKids = beforeBox.children().size();
    	int nowKids = afterBox.children().size();
    	// ★ 둘 다 늘지 않았으면 통과 (삭제 허용 — "복주머니 2개" 유지)
    	if (nowMust <= wasMust && nowKids <= wasKids) return;

    	f.add(Failure.of(FailureCode.ITEM_ADDED, target.key(),
    	        target.key() + " 항목이 늘었습니다. " +
    	        "항목 추가는 서버만 합니다. 문구만 고치세요."));
    }

    // ── 보존 검사 ──────────────────────────────────────────────────

    /**
     * 건드리면 안 되는 "이름표" 가 그대로인가. **개수가 아니라 집합을 본다.**
     *
     * ★ 왜 개수가 아닌가
     *   템플릿 스크립트는 5종 전부 이벤트 위임이다 —
     *   document 에 리스너 하나 걸고 closest('.cta-btn, .btn') 으로 판정한다.
     *   그래서 **버튼을 복제해도 그대로 동작한다.**
     *   개수를 묶으면 "복주머니를 2개만 보여줘"(= 카드 삭제)가 영원히 실패한다.
     *   계약 3표의 "혜택/단계 카드 추가·삭제" 도 막힌다.
     *
     * ★ 대신 이 둘은 고정이다
     *   data-slot  서버가 값을 쓰는 자리. 없어지면 그 이벤트는 영영 못 채운다
     *   id         getElementById 로 직접 찾는다 (#demoTimer · #regCount · #pouchSection)
     *   behavior   data-behavior 이름 — runtime.js 가 이걸 보고 동작한다
     *              없어지면 타이머가 멈추고, 복제하면 id 중복으로 첫 번째만 잡힌다
     *   class      단, **이름표 붙은 요소의 것만.** 아래 diffClasses 참고
     */
    private static void checkPreserved(String before, String after, List<Failure> f) {
        diff(Slots.keysOf(before), Slots.keysOf(after), f,
                FailureCode.SLOT_LOST, "data-slot=\"%s\" 가 붙은 태그를 지웠습니다. "
                        + "그 자리는 서버가 채우는 곳입니다. 태그와 속성을 원래대로 두세요.",
                FailureCode.SLOT_INVENTED, "data-slot=\"%s\" 를 새로 만들었습니다. "
                        + "data-slot 은 서버만 심습니다. 원래 있던 것만 그대로 두세요.");

        diff(Slots.idsOf(before), Slots.idsOf(after), f,
                FailureCode.ID_LOST, "id=\"%s\" 를 지웠습니다. 화면 기능이 그 id 로 요소를 찾습니다. "
                        + "원래 있던 id 를 그대로 두세요.",
                FailureCode.ID_INVENTED, "id=\"%s\" 를 새로 만들었습니다. "
                        + "id 는 화면 기능이 쓰는 이름이라 임의로 추가하면 안 됩니다.");

        // ★ 동작도 이름표다. 지우면 버튼이 조용히 죽고, 지어내면 서버가 심지 않은 동작이 생긴다
        diff(Behavior.keysOf(before), Behavior.keysOf(after), f,
                FailureCode.BEHAVIOR_LOST, "data-behavior=\"%s\" 를 지웠습니다. 버튼의 동작이 이 속성에 걸려 있습니다. "
                        + "원래 있던 data-behavior 를 그대로 두세요.",
                FailureCode.BEHAVIOR_INVENTED, "data-behavior=\"%s\" 를 새로 만들었습니다. "
                        + "동작은 서버만 붙입니다. 원래 있던 것만 그대로 두세요.");

        // ★ 버튼은 늘 수 없다. 수정 정화는 원래 버튼을 지키려고 <button> 을 허용하는데,
        //   그 틈으로 동작(data-behavior) 없는 버튼이 들어오면 눌러도 아무 일이 없는 버튼이 게시된다.
        //   줄어드는 것(카드 삭제)은 허용한다. 카드 추가는 서버가 먼저 복제하므로(duplicateCard) 개수가 같다
        int wasButtons = Jsoup.parseBodyFragment(before == null ? "" : before).select("button").size();
        int nowButtons = Jsoup.parseBodyFragment(after == null ? "" : after).select("button").size();
        if (nowButtons > wasButtons) {
            f.add(Failure.of(FailureCode.BUTTON_INVENTED,
                    "버튼(<button>)을 새로 만들었습니다. 버튼은 동작이 붙어야 해서 서버만 만듭니다. "
                            + "원래 있던 버튼만 그대로 두고 문구만 고치세요."));
        }

        diffClasses(Slots.classMap(before), Slots.classMap(after), f);
    }

    /**
     * 이름표 붙은 요소(root · slot · id)의 class 가 그대로인가.
     *
     * ★ 왜 이것만 보는가는 Slots.classMap() 의 주석에 있습니다.
     *   한 줄로: 전체를 묶으면 "카드 하나 지워줘" 가 영원히 실패합니다.
     *
     * ★ 없어진 요소는 여기서 말하지 않는다
     *   요소 자체가 사라진 경우는 바로 위 slot_lost_ / id_lost_ 가 이미 잡았습니다.
     *   여기서 또 말하면 재시도 프롬프트에 같은 사고가 두 줄로 들어가고,
     *   모델이 뭘 고쳐야 하는지 흐려집니다.
     *
     * ★ 새로 생긴 이름표도 여기서 말하지 않는다 (slot_invented_ / id_invented_ 담당)
     */
    private static void diffClasses(Map<String, String> was, Map<String, String> now,
                                    List<Failure> f) {
        for (Map.Entry<String, String> e : was.entrySet()) {
            String at = e.getKey();
            String after = now.get(at);
            if (after == null) continue;              // 요소가 없어진 건 위에서 잡았다
            if (e.getValue().equals(after)) continue;

            f.add(Failure.of(FailureCode.CLASS_CHANGED, at,
                    at + " 의 class 를 바꿨습니다. 디자인과 버튼 동작이 이 class 에 걸려 있습니다. "
                            + "원래대로 두세요: class=\"" + e.getValue() + "\""));
        }
    }

    private static void diff(Set<String> was, Set<String> now, List<Failure> f,
                             FailureCode lostCode, String lostMsg,
                             FailureCode newCode, String newMsg) {
        for (String k : was) {
            if (!now.contains(k)) f.add(Failure.of(lostCode, k, String.format(lostMsg, k)));
        }
        for (String k : now) {
            if (!was.contains(k)) f.add(Failure.of(newCode, k, String.format(newMsg, k)));
        }
    }

    // ── 정화 ──────────────────────────────────────────────────────

    /**
     * style 속성에 허용할 CSS 속성. **허용 목록이다. 금지 목록이 아니다.**
     * 채팅으로 하는 수정이 "색 바꿔줘 / 글씨 키워줘" 수준이라 이 정도면 충분하다.
     *
     * position · z-index · transform 을 빼둔 이유:
     *   화면을 덮는 요소를 만들 수 있다. 검토 안 된 출력에 허용할 이유가 없다.
     */
    private static final Set<String> ALLOWED_CSS = Set.of(
            "color", "background-color",
            "font-size", "font-weight", "font-style", "line-height",
            "text-align", "text-decoration",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
            "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
            "border", "border-color", "border-width", "border-style", "border-radius");

    /**
     * 생성 결과 정화 — 모델이 만든 문서에 건다.
     *
     * 지우는 것: data-slot · button · input · id · 인터랙션 data-* 속성
     *
     * ★ 왜 지우나
     *   백지 생성 결과에는 그걸 물고 동작할 템플릿 스크립트가 없다.
     *   모델이 버튼을 만들어도 눌리지 않는 껍데기가 생길 뿐이고,
     *   data-slot 을 만들면 서버의 쓰기 지점을 모델이 만드는 셈이 된다 (REQ-LLM-46).
     */
    public static String sanitizeGenerated(String html) {
        return clean(html, false);
    }

    /**
     * 수정 결과 정화 — 템플릿에서 온 블록에 건다.
     *
     * 남기는 것: data-slot · button · input · id · 인터랙션 data-* 속성
     *
     * ★ 생성과 반대다. 원본에 이미 있던 것을 보존해야 하기 때문이다.
     *   계약 EVENT_STRUCTURE_CONTRACT §4-4 가 명시적으로 요구한다 —
     *   "data-demo-msg, data-state 를 반드시 보존", "#demoTimer, #regCount, #pouchSection 유지".
     *
     *   대신 모델이 지어낸 것은 validateEdited 의 checkPreserved 가 잡는다.
     *   **"보존" 과 "대조" 는 한 쌍이다.** 하나만 있으면 뚫린다.
     *
     * ★ on* 핸들러는 걱정하지 않아도 된다
     *   Safelist 는 명시 허용만 하므로 onclick 은 애초에 못 들어온다.
     *   계약 4-⑤ 도 "CTA 버튼에 onclick 인라인 핸들러를 넣지 않습니다" 로 같은 방향이다.
     */
    public static String sanitizeEdited(String html) {
        return clean(html, true);
    }

    /**
     * ★ 이 함수는 "모델이 준 조각" 에만 건다.
     *   서버가 조립한 최종 문서에 걸면 문서 밖 script 가 다 죽는다.
     *   순서:  sanitize(모델 출력) → merge   (반대로 하지 말 것)
     */
    private static String clean(String html, boolean keepInteractive) {
        Safelist s = Safelist.relaxed()
                .addAttributes(":all", "style", "data-block", "class")
                .addTags("section")
                // ★ JS 없이 동작하는 것만 연다.
                //   details/summary  아코디언 FAQ — 브라우저가 펼치고 접는다. open 은 처음 펼친 상태
                //   mark             형광펜 강조 · hr 구분선 · figure/figcaption 그림 설명
                //   button · input · dialog 는 열지 않는다. 동작을 붙일 스크립트가 생성 결과에는 없다.
                .addTags("details", "summary", "mark", "hr", "figure", "figcaption")
                .addAttributes("details", "open")
                // ★ href="#" 를 살리는 줄. 빼면 CTA 버튼의 링크가 통째로 사라진다.
                //   Safelist.relaxed() 는 a[href] 에 ftp/http/https/mailto 만 허용하고,
                //   상대 URL 은 절대 URL 로 바꾼 뒤 검사한다. baseUri 가 비어 있으면
                //   "#" 은 빈 문자열이 되어 어느 프로토콜에도 안 맞고 속성째 제거된다.
                .addProtocols("a", "href", "#");

        if (keepInteractive) {
            s = s.addTags("button", "input")
                 .addAttributes(":all",
                         "data-slot",      // 서버가 값을 쓰는 자리
                         "id",             // getElementById 로 찾는 것들
                         "type",           // <button type="button">
                         "value", "placeholder",
                         "data-href")      // <button> 일 때 서버가 넣는 링크
                 // ★ 동작(data-behavior)과 그것이 읽는 data-* 만. 목록은 Behavior 가 정한다
                 .addAttributes(":all", Behavior.ATTRIBUTES.toArray(String[]::new));
        }
        String cleaned = Jsoup.clean(html, "", s, new Document.OutputSettings().prettyPrint(false));

        // ★ Jsoup 은 style 속성이 "있는지"만 보고 "값"은 보지 않는다. 값은 여기서 거른다.
        Document doc = Jsoup.parseBodyFragment(cleaned);
        doc.outputSettings().prettyPrint(false);
        for (Element el : doc.body().select("[style]")) {
            String safe = cleanStyle(el.attr("style"));
            if (safe.isEmpty()) el.removeAttr("style");
            else el.attr("style", safe);
        }
        cleanLooks(doc);
        // ★ 속성은 열었지만 값은 아직 안 봤다 — 목록에 없는 behavior 이름은 여기서 지운다
        if (keepInteractive) Behavior.sanitize(doc);
        return doc.body().html();
    }

    /**
     * 모양 변형(v-*)과 팔레트(palette-*) class 를 허용 목록으로 거른다.
     *
     * ★ Jsoup Safelist 는 class 속성이 "있는지"만 본다 — style 과 같은 문제다.
     *   모델이 지어낸 v-* 는 CSS 가 없어 효과가 없지만, 남겨 두면
     *   다음 수정 때 모델이 그걸 보고 따라 쓴다. 들어오는 자리에서 지운다.
     *
     * ★ 팔레트는 hero 섹션에만, 하나만 남긴다. 다른 자리의 것은 지운다.
     *   서버가 저장 직전에 루트로 옮긴다(PageShell.hoistPalette).
     *
     * ★ 문구 꾸밈(t-*)도 같은 방식이다 — {@link Inline#sanitize}
     *
     * ★ 접두사가 다른 class 는 건드리지 않는다 — 템플릿 class, btn 등
     */
    private static void cleanLooks(Document doc) {
        for (Element el : doc.body().select("[class]")) {
            Block b = el.is("section[data-block]")
                    ? Block.find(el.attr("data-block")).orElse(null)
                    : null;

            if (b != null) {
                Variant.sanitize(el, b);
            } else {
                for (String c : List.copyOf(el.classNames())) {
                    if (Variant.looksLike(c)) el.removeClass(c);
                }
            }
            // 문구 꾸밈(t-*)은 span · mark · strong · em 에만. 섹션 루트에 붙은 것도 여기서 떨어진다
            Inline.sanitize(el);

            boolean paletteKept = false;
            for (String c : List.copyOf(el.classNames())) {
                if (!Palette.looksLike(c)) continue;
                boolean ok = b == Block.HERO && !paletteKept && Palette.find(c).isPresent();
                if (ok) paletteKept = true;
                else el.removeClass(c);
            }

            // ★ 테마도 팔레트와 같은 규칙이다 — hero 에 하나만, 목록에 있는 것만.
            //   지어낸 이름(theme-두쫀쿠)은 CSS 가 없어 효과도 없지만, 남겨 두면
            //   다음 수정에서 모델이 그걸 보고 따라 쓴다. 들어오는 자리에서 지운다.
            //
            // ★★ 단, **루트(.ev-container)는 건드리지 않는다.**
            //   이 반복문은 class 가 있는 모든 요소를 돈다. 루트는 section 이 아니라
            //   b == null 이 되어 "hero 가 아니다" 로 판정되고, 그대로 두면 템플릿이
            //   들고 온 theme-sports 가 지워진다. 그러면 PageShell.ensureRoot 가
            //   "테마 없음" 으로 보고 기본 테마를 박아 **템플릿 테마가 전부 basic 이 된다**
            //   (SavedTemplateHtmlTest 5건으로 실제로 잡혔다).
            //   루트의 테마는 서버(PageShell)가 쥔다. 여기는 모델 출력만 다룬다.
            //
            // ★ 팔레트 쪽에 같은 가드가 없는 것은 SavedTemplateHtml 이 루트 팔레트를
            //   따로 집어 두었다가 되붙여서 가려져 있을 뿐이다. 테마에는 그 보정이 없다.
            //
            // ★ 기본 테마를 "넣는" 일은 여기서 하지 않는다. 이 함수는 RetryService 를 거쳐
            //   **수정 경로도** 지난다. 수정마다 블록에 theme-* 가 박히면 루트 테마가
            //   매번 리셋되고, EditService 의 "안 바뀌었으면 실패" 검사도 전후가 늘
            //   달라져 무력해진다. 기본값은 PageShell.ensureRoot 한 곳에서만 박는다.
            boolean isRoot = PageShell.isRoot(el);
            boolean themeKept = false;
            for (String c : List.copyOf(el.classNames())) {
                if (isRoot || !Theme.looksLike(c)) continue;
                boolean ok = b == Block.HERO && !themeKept && Theme.find(c).isPresent();
                if (ok) themeKept = true;
                else el.removeClass(c);
            }
            if (el.classNames().isEmpty()) el.removeAttr("class");
        }
    }

    /**
     * 서버가 카드를 먼저 복제한다. 모델은 문구만 채운다.
     *
     * ★ 복제한 카드의 글자는 비운다. 구조·class·data-slot 은 그대로 둔다.
     *   모델이 받은 HTML에 이미 4개가 있으므로 전후 개수가 같아 검사를 통과한다.
     *   모델이 스스로 늘리면 3→4로 걸린다.
     *
     * ★ container() 가 null 이면 그대로 돌려준다.
     */
    public static String duplicateCard(String html, Block target) {
    	if (target.container() == null) return html;

    	Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);
    	Element box = doc.body().selectFirst(target.container());
    	if(box == null || box.children().isEmpty()) return html;

    	Element src = box.select(target.must()).last(); // 이후
    	if (src == null) return html;

    	Element copy = src.clone();
    	emptyText(copy);
    	src.parent().appendChild(copy);
    	return doc.body().html();
    }

    /** style="..." 안에서 허용 목록에 있는 선언만 남긴다 */
    private static String cleanStyle(String style) {
        StringBuilder out = new StringBuilder();
        for (String decl : style.split(";")) {
            int i = decl.indexOf(':');
            if (i < 0) continue;

            String prop = decl.substring(0, i).trim().toLowerCase();
            String val  = decl.substring(i + 1).trim();
            if (val.isEmpty() || !ALLOWED_CSS.contains(prop)) continue;

            // 허용 속성이어도 값 안에 숨을 수 있는 것들
            String low = val.toLowerCase();
            if (low.contains("url(") || low.contains("expression(")
                    || low.contains("javascript:") || low.contains("@import")
                    || low.contains("/*") || low.contains("\\")) continue;

            out.append(prop).append(':').append(val).append(';');
        }
        return out.toString();
    }

    /**
     * 모델이 뭘 돌려주든 그 블록 자리에만 끼워 넣는다.
     * 실험에서 모델이 "조각만" 또는 "전체를" 돌려주는 게 제각각이었고
     * 프롬프트로 통제되지 않았습니다. 서버가 판별해서 병합합니다.
     */
    public static String merge(String currentDoc, Block target, String modelOutput) {
        Document cur = Jsoup.parseBodyFragment(currentDoc);
        Document out = Jsoup.parseBodyFragment(modelOutput);

        Element incoming = out.body().selectFirst(target.selector());
        if (incoming == null) return currentDoc;      // 대상 블록이 없으면 원본 유지

        Element old = cur.body().selectFirst(target.selector());
        if (old == null) cur.body().appendChild(incoming);
        else old.replaceWith(incoming);

        return cur.body().html();
    }

    /** 문서에서 블록 하나만 떼어낸다 — 수정 요청과 validateEdited 의 before 로 쓴다 */
    public static String blockOf(String doc, Block target) {
        Element el = Jsoup.parseBodyFragment(doc).body().selectFirst(target.selector());
        return el == null ? "" : el.outerHtml();
    }

    /**
     * ★ 글자와 내용성 속성값을 비운다. 자식 태그·속성 구조는 둔다.
     *   text("")는 자식 요소까지 지워서 버튼 등이 사라진다.
     *   data-demo-msg 는 카드별 문구라 복사하면 원본 안내가 뜬다.
     *   없으면 프론트가 '(데모)' 를 보여준다.
     */
    private static void emptyText(Element el) {
        for (TextNode t : new ArrayList<>(el.textNodes())) t.remove();
        for (Element d : el.select("*")) {
            for (TextNode t : new ArrayList<>(d.textNodes())) t.remove();
            d.removeAttr("data-demo-msg");   // 추가
        }
        el.removeAttr("data-demo-msg");      // 추가 (자기 자신도)
    }
}
