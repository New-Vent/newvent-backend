package com.newvent.registry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

/**
 * 저장되는 조각이 **혼자서도 성립하도록** 서버가 보장하는 것들.
 *
 * ★ 왜 이게 필요했나 — 두 경로의 계약이 달랐다
 *     템플릿  조각 안에 &lt;div class="ev-container event-page"&gt; 가 있다. 테마 클래스는 없다.
 *     백지    래퍼가 아예 없다. &lt;section&gt; 으로 바로 시작한다.
 *
 *   event.css 의 규칙은 **전부** .ev-container 또는 .event-page 의 자손 선택자다.
 *
 *       :where(.event-page, .ev-container) :where([data-block="hero"]:not(.ev-block)) { … }
 *
 *   그래서 백지 결과는 CSS 가 하나도 안 걸렸고, 템플릿 결과는
 *   .theme-sports .sp-* 가 전부 빗나갔다. 카드 기본 스타일마저
 *   :not([class*="sp-"]) 로 sp- 를 제외하므로 fallback 도 안 걸린다 —
 *   즉 테마 클래스가 없으면 혜택 카드는 **양쪽 다 안 먹어서** 무스타일이다.
 */
public final class PageShell {

    /** 래퍼에 붙는 class. 둘 다 붙인다 — event.css 가 두 이름을 다르게 쓴다. */
    public static final String ROOT_CLASSES = "ev-container event-page";

    /** 래퍼를 찾는 선택자. 둘 중 하나라도 있으면 이미 래퍼가 있는 것이다. */
    private static final String ROOT_SELECTOR = ".ev-container, .event-page";

    /** 관리자가 정한 블록 순서. 래퍼에 붙는다 — 자세한 건 {@link #readOrder} 주석에 있다. */
    private static final String ORDER_ATTR = "data-block-order";


    /** 이 요소가 래퍼인가. 이름은 ROOT_SELECTOR 한 곳에만 둔다. */
    public static boolean isRoot(Element el) {
        return el != null && el.is(ROOT_SELECTOR);
    }

    /** 범위 안에서 래퍼를 찾는다. 없으면 null. */
    public static Element rootOf(Element scope) {
        return scope == null ? null : scope.selectFirst(ROOT_SELECTOR);
    }

    private static final String NOTICES_RESOURCE = "/notices/common.html";

    /**
     * 승인 문구. 한 번 읽고 들고 있는다.
     *
     * ★ 왜 리소스 파일인가
     *   문구가 바뀌면 자바 코드를 고치지 않고 파일 하나만 고친다. 기획·법무가 읽을 수 있고,
     *   PR 에서 문구 변경만 따로 보인다. 관리자 화면이 생기면 DB 로 옮기면 되고,
     *   그때 바뀌는 건 NOTICES_RESOURCE 한 줄이다.
     *
     * ★ 왜 static final 초기화가 아닌가
     *   거기서 던지면 자바가 클래스를 "초기화 실패" 로 기억한다. 그 뒤 접근은 전부
     *   NoClassDefFoundError 가 되고, **진짜 원인은 첫 줄에만 남는다.**
     *   테스트 로그가 파일 누락 하나에 도배되고 원인을 찾기 어려워진다.
     *   지연 로딩이면 부를 때마다 같은 메시지가 그대로 나온다.
     */
    private static volatile String noticesHtml;

    private PageShell() {}

    /** 게시된 HTML 조각을 독립적인 문서로 감싼다. 조각의 스크립트와 마크업은 그대로 유지한다. */
    public static String standalone(String fragment, String title) {
        String safeTitle = new Element("title").text(title).outerHtml();
        return "<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + safeTitle + "\n"
                + "<link rel=\"stylesheet\" href=\"/assets/event.css\">\n"
                + "<link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@100..900&amp;display=swap\">\n"
                + "</head>\n<body>\n" + fragment + "\n</body>\n</html>";
    }

    /**
     * 승인 문구를 읽을 수 있는지 확인한다. **시작할 때 부른다.**
     *
     * ★ 왜 시작할 때인가
     *   유의사항 없는 페이지가 게시되는 것이 이 클래스가 막으려는 바로 그 일이다.
     *   그걸 첫 생성 요청 때 알게 되면 이미 늦다. 뜨지 않는 편이 낫다.
     *   설정 클래스에서 한 줄 부르면 된다:  PageShell.selfCheck();
     */
    public static void selfCheck() {
        notices();
    }

    private static String notices() {
        String v = noticesHtml;
        if (v == null) {
            v = loadNotices();
            noticesHtml = v;
        }
        return v;
    }

    /**
     * 저장 직전에 한 번 부른다. 래퍼 → 테마 → 유의사항 순서다.
     *
     * @param templateCode 템플릿 코드. **백지면 null** — 그때는 모델이 고른 테마,
     *                     그것도 없으면 {@link Theme#DEFAULT} 가 붙는다.
     */
    public static String plant(String fragment, String templateCode) {
        return ensureNotices(ensureRoot(fragment, templateCode));
    }

    /**
     * 루트 래퍼를 보장한다. 없으면 만들어 감싸고, 있으면 class 만 보강한다.
     *
     * ★ 이미 theme-* 가 붙어 있으면 건드리지 않는다.
     *   관리자가 만든 템플릿이 자기 테마를 들고 올 수 있다.
     * ★ 테마가 하나도 없으면 반드시 하나 박는다 — 템플릿이면 코드로, 백지면 기본 테마로.
     */
    public static String ensureRoot(String fragment, String templateCode) {
        Document doc = parse(fragment);
        Element root = doc.body().selectFirst(ROOT_SELECTOR);

        if (root == null) {
            Element wrapper = doc.body().appendElement("div");
            // ★ appendChild 가 부모를 옮긴다. 순회 중에 옮기면 인덱스가 흔들리므로 먼저 복사한다.
            List<Node> moving = new ArrayList<>(doc.body().childNodes());
            for (Node n : moving) {
                if (n == wrapper) continue;
                wrapper.appendChild(n);
            }
            root = wrapper;
        }

        for (String c : ROOT_CLASSES.split(" ")) {
            if (!root.hasClass(c)) root.addClass(c);
        }

        final Element target = root;
        orderBlocks(target);
        // ★ 먼저 올린다. 모델이 hero 에 고른 테마 · 팔레트를 루트로 옮긴다.
        //   순서를 뒤집으면 기본 테마가 먼저 박혀서 모델이 고른 테마가 밀린다.
        hoist(doc, target);

        // ★ 기본 테마는 여기서만 박는다 — BlockValidator.cleanLooks 에 넣으면 안 된다.
        //   cleanLooks 는 RetryService 를 거쳐 **수정 경로도** 지난다. 거기서 기본값을
        //   넣으면 수정마다 블록에 theme-* 가 박혀 루트 테마가 리셋되고,
        //   EditService 의 "안 바뀌었으면 실패" 검사도 전후가 늘 달라져 무력해진다.
        //
        // ★ 테마가 없으면 event.css 의 :root 기본값 = 같은 흰 카드가 세로로 쌓인 화면이다.
        //   Theme.DEFAULT(BASIC)는 성격이 없어서 어떤 이벤트에 박혀도 틀리지 않는다.
        boolean themed = target.classNames().stream().anyMatch(Theme::looksLike);
        if (!themed) {
            target.addClass(Theme.of(templateCode).orElse(Theme.DEFAULT).cssClass());
        }
        return doc.body().html();
    }

    /**
     * 수정 결과를 저장하기 직전에 부른다 — 블록 순서를 맞추고 팔레트를 루트로 옮긴다.
     * 생성 · 템플릿 경로는 {@link #ensureRoot} 가 같은 일을 한다.
     *
     * ★ 루트가 없으면 그대로 돌려준다. 래퍼를 만드는 건 ensureRoot 의 몫이다
     */
    public static String settle(String fragment) {
        Document doc = parse(fragment);
        Element root = doc.body().selectFirst(ROOT_SELECTOR);
        if (root == null) return fragment;
        orderBlocks(root);
        hoist(doc, root);
        return doc.body().html();
    }

    /**
     * 블록을 {@link Block} 선언 순서로 다시 세운다.
     *
     * ★ 왜 서버가 정렬하나 — 모델이 낸 순서를 그대로 저장하면
     *   FAQ 가 참여 버튼 뒤에 붙는다(Bedrock 실측). 순서는 프롬프트로 시키는 것보다
     *   서버가 맞추는 게 확실하고, 레지스트리 순서가 이미 정본이다.
     *
     * ★ 블록 자리만 바꾼다. 블록 사이의 다른 노드(템플릿 스크립트 · 장식 div)는 제자리에 둔다 —
     *   원래 블록들이 있던 자리(슬롯)에 정렬된 블록을 차례로 꽂는다.
     * ★ 이미 순서가 맞으면 아무것도 안 한다. 템플릿 5종은 이미 맞다 (바이트 왕복 테스트 유지).
     * ★ 형제끼리만 정렬한다. 모르는 data-block 은 세지 않고 제자리에 둔다.
     */
    private static void orderBlocks(Element root) {
        List<Element> slots = new ArrayList<>();
        for (Element child : root.children()) {
            if (child.is("section[data-block]") && Block.find(child.attr("data-block")).isPresent()) {
                slots.add(child);
            }
        }
        List<Block> wanted = readOrder(root);
        List<Element> sorted = new ArrayList<>(slots);
        sorted.sort(java.util.Comparator.comparingInt(
                e -> rank(wanted, Block.of(e.attr("data-block")))));
        if (sorted.equals(slots)) return;

        List<Element> markers = new ArrayList<>(slots.size());
        for (Element s : slots) {
            Element marker = new Element("template");
            s.before(marker);
            markers.add(marker);
        }
        for (Element s : slots) s.remove();
        for (int i = 0; i < markers.size(); i++) markers.get(i).replaceWith(sorted.get(i));
    }

    /**
     * 관리자가 정한 순서를 읽는다. 없으면 빈 목록 — 그러면 레지스트리 순서가 된다.
     *
     * ★ 왜 DB 컬럼이 아니라 HTML 속성인가
     *   - 모델은 이 속성을 심을 수 없다. BlockValidator.sanitizeEdited 의 Safelist 가
     *     data-block 만 허용해서 모델 출력의 data-block-order 는 지워진다.
     *     즉 **서버만 쓸 수 있는 자리**다.
     *   - HTML 안에 있으니 버전을 복원하면 순서도 같이 복원된다. 컬럼이면 따로 맞춰야 한다.
     *   - 마이그레이션이 필요 없다.
     *
     * ★ 모르는 이름과 중복은 조용히 버린다. 프론트가 블록 하나를 빼먹어도
     *   그 블록이 사라지면 안 된다 — rank() 가 레지스트리 순서로 뒤에 붙인다.
     */
    private static List<Block> readOrder(Element root) {
        String raw = root.attr(ORDER_ATTR);
        if (raw.isBlank()) return List.of();

        List<Block> out = new ArrayList<>();
        for (String key : raw.split(",")) {
            Block.find(key.trim()).filter(b -> !out.contains(b)).ifPresent(out::add);
        }
        return out;
    }

    /**
     * 정렬 기준. 작을수록 앞이다.
     *
     *   0 ~  99 : 관리자가 정한 순서 (목록에 적힌 자리)
     * 100 ~ 199 : 목록에 없는 블록 — 레지스트리 순서로 그 뒤에
     * 200 ~     : 고정 블록(canMove()==false) — 목록과 무관하게 맨 끝, 레지스트리 순서로
     *
     * ★ 목록이 비면 모든 블록이 100번대·200번대로 가고, 둘 다 레지스트리 순서다.
     *   = 지금까지의 동작과 **글자 하나 다르지 않다.** 템플릿 5종의 바이트 왕복도 그대로다.
     */
    private static int rank(List<Block> wanted, Block b) {
        if (!b.canMove()) return 200 + b.ordinal();
        int at = wanted.indexOf(b);
        return at >= 0 ? at : 100 + b.ordinal();
    }

    /**
     * 관리자가 정한 순서를 적고 그대로 세운다. **순서 변경 API 가 부르는 입구다.**
     *
     * ★ 모델을 부르지 않는다. 삭제와 같다 — 순서를 바꾸는 데 모델이 필요 없다.
     * ★ 고정 블록을 목록에 넣어도 무시된다(rank 참고). 프론트를 믿지 않는다.
     * ★ 루트가 없으면 그대로 돌려준다. 래퍼를 만드는 건 ensureRoot 의 몫이다.
     */
    public static String applyOrder(String fragment, List<Block> order) {
        Document doc = parse(fragment);
        Element root = doc.body().selectFirst(ROOT_SELECTOR);
        if (root == null) return fragment;

        // ★ 중복과 고정 블록은 여기서 버린다. 프론트를 믿지 않는다 —
        //   읽을 때(readOrder)도 한 번 더 거르지만, 저장되는 값부터 깨끗한 게 맞다
        StringJoiner keys = new StringJoiner(",");
        Set<Block> seen = EnumSet.noneOf(Block.class);
        for (Block b : order) {
            if (b.canMove() && seen.add(b)) keys.add(b.key());
        }
        // ★ 빈 속성은 남기지 않는다. 옮길 게 하나도 없으면 레지스트리 순서로 되돌린다는 뜻이다
        if (keys.length() == 0) root.removeAttr(ORDER_ATTR);
        else root.attr(ORDER_ATTR, keys.toString());
        orderBlocks(root);
        return doc.body().html();
    }

    /**
     * 지금 문서에 있는 블록을 보이는 순서대로. **순서 변경 화면이 목록을 그릴 때 쓴다.**
     */
    public static List<Block> blocksOf(String fragment) {
        List<Block> out = new ArrayList<>();
        for (Element el : parse(fragment).body().select("section[data-block]")) {
            Block.find(el.attr("data-block")).filter(b -> !out.contains(b)).ifPresent(out::add);
        }
        return out;
    }

    /**
     * hero 섹션에 붙은 팔레트를 루트로 옮긴다. **수정 결과를 저장하기 직전에 부른다.**
     * 생성 · 템플릿 경로는 {@link #ensureRoot} 가 같은 일을 한다.
     *
     * ★ 왜 옮기나 — 모델은 블록 하나만 출력한다. 루트를 못 만진다.
     *   그래서 hero 에 고르게 하고, 색은 페이지 전체(루트)에 걸어야 하므로 서버가 옮긴다.
     *   hero 에 남겨 두면 hero 안쪽만 색이 바뀐다.
     *
     * ★ 루트가 없으면 그대로 돌려준다. 래퍼를 만드는 건 ensureRoot 의 몫이다
     */
    public static String hoistPalette(String fragment) {
        Document doc = parse(fragment);
        Element root = doc.body().selectFirst(ROOT_SELECTOR);
        if (root == null) return fragment;
        hoist(doc, root);
        return doc.body().html();
    }

    /**
     * 섹션들의 palette-* · theme-* 를 걷어 루트에 하나만 남긴다.
     *   새 팔레트가 없으면     루트는 그대로 (이번 수정이 색을 안 건드렸다)
     *   palette-base 면        루트의 팔레트를 지운다 (원래대로)
     *   그 밖                  루트의 팔레트를 그걸로 바꾼다
     */
    private static void hoist(Document doc, Element root) {
        String palette = pickFromHero(doc, Palette::looksLike, c -> Palette.find(c).isPresent());
        String theme   = pickFromHero(doc, Theme::looksLike,   c -> Theme.find(c).isPresent());

        if (palette != null) {
            for (String c : List.copyOf(root.classNames())) {
                if (Palette.looksLike(c)) root.removeClass(c);
            }
            if (!palette.equals(Palette.BASE.cssClass())) root.addClass(palette);
        }
        // ★ 테마에는 base 가 없다. "테마 없음" 은 ensureRoot 가 기본 테마를 박아 막는다
        if (theme != null) {
            for (String c : List.copyOf(root.classNames())) {
                if (Theme.looksLike(c)) root.removeClass(c);
            }
            root.addClass(theme);
        }
    }

    /**
     * hero 섹션에서 첫 유효 값을 집고, <b>모든 섹션에서 그 접두사를 지운다.</b>
     *
     * ★ hero 에서만 집는 이유 — 페이지 전체 값이라 자리가 하나여야 한다. 블록마다 다른 걸
     *   고르면 "마지막에 이긴 것" 이 되어 수정할 때마다 결과가 달라진다. 프롬프트가
     *   hero 에만 안내하므로 서버도 hero 만 믿는다.
     * ★ hero 가 아닌 자리의 것은 집지 않고 지운다 — 남겨 두면 다음 수정에서
     *   모델이 그걸 보고 따라 쓴다(v-* 에서 겪은 그 경로다).
     */
    private static String pickFromHero(Document doc,
                                       Predicate<String> looksLike,
                                       Predicate<String> known) {
        String picked = null;
        for (Element sec : doc.body().select("section[data-block]")) {
            for (String c : List.copyOf(sec.classNames())) {
                if (!looksLike.test(c)) continue;
                if (picked == null && known.test(c) && sec.is(Block.HERO.selector())) {
                    picked = c;
                }
                sec.removeClass(c);
            }
            if (sec.classNames().isEmpty()) sec.removeAttr("class");
        }
        return picked;
    }

    /**
     * 유의사항 블록을 보장한다. 모델은 만들 수 없고(SERVER 소유) 서버만 넣는다.
     *
     * ★ 자리는 Block 순서를 따른다 — HERO → BENEFITS → STEPS → NOTICES → CTA.
     *   뒤에 오는 블록 중 **실제로 있는 첫 번째** 앞에 넣는다. 아무것도 없으면 맨 끝이다.
     *   하드코딩으로 "CTA 앞" 이라고 쓰면 CTA 가 없는 문서에서 자리가 틀린다.
     */
    public static String ensureNotices(String fragment) {
        Document doc = parse(fragment);
        if (doc.body().selectFirst(Block.NOTICES.selector()) != null) {
            return doc.body().html();          // 템플릿에는 이미 있다
        }

        Element host = doc.body().selectFirst(ROOT_SELECTOR);
        if (host == null) host = doc.body();

        Element notices = parse(notices()).body().child(0);

        Element anchor = null;
        for (Block b : Block.values()) {
            if (b.ordinal() <= Block.NOTICES.ordinal()) continue;
            Element found = host.selectFirst(b.selector());
            if (found != null) { anchor = found; break; }
        }

        if (anchor != null) anchor.before(notices);
        else                host.appendChild(notices);

        return doc.body().html();
    }

    // hero 블록만 잘라낸다 - 목록 썸네일용 hero 가 없으면 null
    public static String heroOnly(String fragment) {
        Document doc = parse(fragment);
        Element hero = doc.body().selectFirst(Block.HERO.selector());
        if (hero == null) return null;

        Element root = doc.body().selectFirst(ROOT_SELECTOR);
        if (root == null) return hero.outerHtml();

        Element shell = root.shallowClone();
        shell.appendChild(hero.clone());
        return shell.outerHtml();
    }

    private static Document parse(String html) {
        Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);
        // ★ 조각의 공백을 그대로 둔다. 여기서 재정렬하면 버전 diff 가 통째로 번진다.
        doc.outputSettings().prettyPrint(false);
        return doc;
    }

    /**
     * 클래스패스에서 문구를 읽는다.
     *
     * ★ 클래스로더를 두 군데 본다
     *   보통은 이 클래스의 로더로 충분하지만, 테스트 러너나 devtools 처럼
     *   로더가 갈리는 환경에서 한쪽만 보면 "파일은 있는데 못 찾는" 일이 생긴다.
     */
    private static String loadNotices() {
        try (InputStream in = open()) {
            if (in == null) {
                throw new IllegalStateException(
                        "승인 유의사항 문구를 클래스패스에서 못 찾았습니다: " + NOTICES_RESOURCE
                        + "\n  두어야 할 곳: src/main/resources" + NOTICES_RESOURCE
                        + "\n  빌드 산출물: build/resources/main" + NOTICES_RESOURCE
                        + "\n  산출물에 없으면 ./gradlew clean 후 다시 빌드하세요."
                        + "\n  이 파일이 없으면 유의사항 없는 페이지가 게시됩니다.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("승인 유의사항 문구를 읽지 못했습니다: " + NOTICES_RESOURCE, e);
        }
    }

    private static InputStream open() {
        InputStream in = PageShell.class.getResourceAsStream(NOTICES_RESOURCE);
        if (in != null) return in;

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        // ★ 컨텍스트 로더는 앞의 / 를 붙이지 않는다. 붙이면 못 찾는다.
        return (cl == null) ? null : cl.getResourceAsStream(NOTICES_RESOURCE.substring(1));
    }
}
