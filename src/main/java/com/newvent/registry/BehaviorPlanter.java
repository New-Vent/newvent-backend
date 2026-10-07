package com.newvent.registry;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 서버가 behavior 를 심는 곳. **모델은 behavior 를 만들지 않는다** — {@link Behavior}.
 *
 *   plantGenerated   백지 생성 결과 — 저장 전에. 이벤트 값과 무관한 것만
 *                      hero    countdown 자리 · 혜택(또는 참여 방법)으로 가는 scroll-to 버튼
 *                      cta     participate
 *   bind             보여 줄 때 이벤트 값을 묶는다 — countdown 의 종료 시각
 *
 * ★ 이벤트 값(기간)은 저장본에 굳히지 않는다
 *   게시 뒤에도 바뀐다. 굳히면 기간을 고쳐도 타이머가 옛 날짜를 센다 — Slots.fill 과 같은 이유로 bind 는 보여 줄 때만 한다.
 *
 * ★ behavior 이전에 저장된 페이지는 바꾸지 않는다
 *   그 페이지들은 옛 &lt;script&gt; 가 그대로 남아 동작한다 — PageShell.standalone 은 behavior 가 없는 문서의 script 를 두고 bind 도 하지 않는다.
 */
public final class BehaviorPlanter {

    private BehaviorPlanter() {}

    /** 이 HTML 안에서 behavior 가 b 인 요소들 — data-behavior 는 공백 목록이라 선택자로는 못 고른다 */
    static List<Element> withBehavior(Element scope, Behavior b) {
        List<Element> out = new ArrayList<>();
        for (Element el : scope.select("[" + Behavior.ATTR + "]")) {
            if (Behavior.parse(el.attr(Behavior.ATTR)).contains(b)) out.add(el);
        }
        return out;
    }

    // ── 배치 한 건 ─────────────────────────────────────────────────

    /**
     * 서버가 심을 동작 한 건.
     *
     * @param block  둘 곳
     * @param target scroll-to 의 이동 대상. 그 밖에는 null
     */
    public record Placement(Behavior behavior, Block block, Block target) {
        public static Placement of(Behavior b, Block block) { return new Placement(b, block, null); }
    }

    /**
     * 배치를 문서에 심는다. 이미 같은 동작이 있으면 두 번 심지 않는다.
     *
     * @return 무엇이라도 심었으면 true
     */
    public static boolean apply(Element scope, List<Placement> placements) {
        boolean changed = false;
        for (Placement p : placements) {
            Element at = scope.selectFirst(p.block().selector());
            if (at == null) continue;
            changed |= switch (p.behavior()) {
                case COUNTDOWN   -> countdown(scope, at);
                case SCROLL_TO   -> jump(scope, at, p.target() == null ? null : scope.selectFirst(p.target().selector()));
                case PARTICIPATE -> participate(at);
                default          -> false;       // Behavior.PLANNABLE 밖은 서버가 심지 않는다
            };
        }
        return changed;
    }

    /** 마감까지 남은 시간. 시각은 보여 줄 때 bind 가 넣는다 */
    static boolean countdown(Element scope, Element at) {
        if (!withBehavior(scope, Behavior.COUNTDOWN).isEmpty()) return false;
        Element p = at.appendElement("p").addClass("ev-countdown");
        p.appendElement("span").addClass("ev-countdown-label").text("마감까지");
        p.appendText(" ");
        p.appendElement("span").addClass("ev-countdown-time").attr(Behavior.ATTR, Behavior.COUNTDOWN.key());
        return true;
    }

    /** 다른 영역으로 내려가는 버튼. 대상에는 서버가 id 를 붙인다 */
    static boolean jump(Element scope, Element at, Element target) {
        if (target == null || target == at || !withBehavior(scope, Behavior.SCROLL_TO).isEmpty()) return false;
        if (target.id().isBlank()) target.id("ev-" + target.attr("data-block"));
        String label = switch (target.attr("data-block")) {
            case "benefits" -> "혜택 자세히 보기 ↓";
            case "steps"    -> "참여 방법 보기 ↓";
            case "cta"      -> "지금 참여하기 ↓";
            case "faq"      -> "자주 묻는 질문 보기 ↓";
            default         -> "자세히 보기 ↓";
        };
        at.appendElement("p").addClass("ev-jump")
                .appendElement("a").attr("href", "#").addClass("btn").addClass("ev-jump-btn")
                .attr(Behavior.ATTR, Behavior.SCROLL_TO.key())
                .attr("data-target", target.id())
                .text(label);
        return true;
    }

    /** 그 영역의 첫 버튼 · 링크를 참여 버튼으로. 이미 동작이 있으면 두지 않는다 */
    static boolean participate(Element at) {
        Element btn = at.selectFirst("a, button");
        if (btn == null || btn.hasAttr(Behavior.ATTR)) return false;
        btn.attr(Behavior.ATTR, Behavior.PARTICIPATE.key());
        return true;
    }

    // ── 백지 생성 ──────────────────────────────────────────────────

    /** 백지 생성의 기본 배치 — 참여 · 카운트다운 · 혜택(없으면 참여 방법)으로 이동 */
    public static List<Placement> defaults(Element scope) {
        List<Placement> out = new ArrayList<>();
        out.add(Placement.of(Behavior.PARTICIPATE, Block.CTA));
        out.add(Placement.of(Behavior.COUNTDOWN, Block.HERO));
        Block target = scope.selectFirst(Block.BENEFITS.selector()) != null ? Block.BENEFITS
                : scope.selectFirst(Block.STEPS.selector()) != null ? Block.STEPS : null;
        if (target != null) out.add(new Placement(Behavior.SCROLL_TO, Block.HERO, target));
        return out;
    }

    /**
     * 백지 생성 결과에 기본 배치를 심는다.
     *
     * ★ 생성 정화가 data-behavior · id 를 지운 **뒤에** 부른다. 순서가 반대면 서버가 심은 것도 지워진다
     */
    public static String plantGenerated(String html) {
        Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);
        doc.outputSettings().prettyPrint(false);
        return apply(doc.body(), defaults(doc.body())) ? doc.body().html() : html;
    }

    /** 배치 결과를 조각으로 — 생성 · 수정 경로 공통 */
    public static String apply(String html, List<Placement> placements) {
        Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);
        doc.outputSettings().prettyPrint(false);
        return apply(doc.body(), placements) ? doc.body().html() : html;
    }

    // ── 보여 줄 때 ─────────────────────────────────────────────────

    /**
     * 이벤트 값을 묶는다 — countdown 의 data-until = 종료 시각. 데모 초(data-seconds)보다 앞선다.
     * **behavior 가 있는 문서에만 부른다** (PageShell.standalone)
     *
     * @param endsAt 이벤트 종료 시각. 없으면 아무것도 하지 않는다
     */
    public static void bind(Document doc, OffsetDateTime endsAt) {
        if (endsAt == null) return;
        String until = endsAt.toInstant().toString();
        withBehavior(doc.body(), Behavior.COUNTDOWN).forEach(el -> el.attr("data-until", until));
    }
}
