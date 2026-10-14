package com.newvent.registry;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * "1번 이미지를 배경으로 깔아줘" 를 <b>모델 없이</b> 처리한다.
 *
 * ★★ 왜 모델을 안 부르나 — 부를 일이 없다.
 *   이 요청에는 정할 게 셋뿐이다: 몇 번 · 어떤 역할 · 어느 영역.
 *   관리자가 그 셋을 이미 말했다. 모델이 더할 판단이 없다.
 *
 * ★★ 그런데 부르면 **망가진다.** 수정 경로는 모델에게 &lt;section&gt; 전체를 주고
 *   전체를 돌려받으므로, 요청과 무관한 것까지 같이 바뀐다. 실제로 난 일들:
 *     "전체 색을 초록색으로"  → 색만 바뀌어야 하는데 올려 둔 &lt;img&gt; 가 사라졌다
 *     "1번을 배경으로"        → 이미지만 들어가야 하는데 문구가 같이 바뀌었다
 *     "2번을 배경으로"        → 먼저 넣어 둔 1번 그림이 사라졌다
 *     "배경 이미지 없애줘"    → 이미지만 빠져야 하는데 문구가 같이 바뀌었다
 *     "배경으로 깔아줘"       → 배경에도 들어가고 혜택 아이콘 자리에도 들어갔다
 *   전부 "모델이 영역을 다시 쓴다" 라는 한 가지에서 나온다. 안 부르면 생기지 않는다.
 *
 * ★ 못 읽으면 조용히 비켜선다. 모호한 요청("이 사진 어울리게 넣어줘")은
 *   모델이 맡는 게 맞다. 여기는 **확실할 때만** 가로챈다.
 *
 * ★ 순수 함수다. DB · 모델 · HTTP 를 모른다 — Variant · Palette 와 같은 자리다.
 */
public final class AssetEdit {

    private AssetEdit() {}

    /**
     * @param index  몇 번째 이미지인가 (1부터). 0 이면 안 적었다는 뜻이다
     * @param role   어떤 배치인가
     * @param block  어느 영역인가
     * @param remove 빼 달라는 요청인가
     * @param left   제목 옆 그림을 왼쪽에 둘 것인가 (ART 에만 쓴다)
     */
    /**
     * @param role  어떤 배치인가. <b>null 이면 "어디 있든"</b> 이라는 뜻이다 —
     *              "1번 이미지 지워줘" 처럼 자리를 안 말하고 빼 달라는 요청에서 그렇다
     * @param block 어느 영역인가. role 이 null 이면 같이 null 이다 (페이지 전체를 본다)
     */
    public record Plan(int index, Asset role, Block block, boolean remove, boolean left) {}

    /**
     * 읽은 결과. 셋뿐이다.
     *
     * ★★ {@link CannotDo} 가 따로 있는 이유 — <b>막힌 조합을 모델에게 넘기면 안 된다.</b>
     *   "혜택 배경에 1번 이미지" 는 관리자가 **또렷하게** 말한 요청인데 우리가 막아 둔
     *   조합이다(혜택은 목록이라 사진을 깔면 안 읽힌다). 비켜서면 모델이 받아서
     *   아무 데나 끼워 넣는다 — 실제로 혜택 카드 하나에 사진이 통째로 들어가
     *   카드가 늘어나고 옆칸이 텅 비었다.
     *   할 수 없으면 할 수 없다고 말하는 쪽이 낫다.
     */
    public sealed interface Result {
        /** 이대로 하면 된다 */
        record Ok(Plan plan) implements Result {}

        /** 또렷한 요청이지만 할 수 없다. 관리자에게 그대로 보여 줄 문구다 */
        record CannotDo(String message) implements Result {}

        /** 이미지 요청이 아니거나 모호하다 — 평소 경로(모델)로 보낸다 */
        record NotMine() implements Result {}
    }

    // ★ "1번" · "2 번" · "3번째" 를 모두 받는다. 띄어쓰기는 관리자마다 다르다
    private static final Pattern NUMBER = Pattern.compile("(\\d+)\\s*번");

    /** 한글 서수. "첫번째 이미지" 처럼 쓰는 사람이 꼭 있다 */
    private static final String[] ORDINALS = {
        "첫", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열",
    };

    /**
     * ★ 순서가 중요하다. "페이지 전체 배경" 이 "배경" 보다 **먼저** 걸려야 한다 —
     *   뒤에 두면 "배경" 이 먼저 맞아서 영역 배경으로 떨어진다.
     */
    private static final Object[][] ROLES = {
        { Asset.PAGE, new String[] { "페이지 전체 배경", "페이지 배경", "전체 배경", "페이지 뒤" } },
        { Asset.BAND, new String[] { "띠", "배너" } },
        { Asset.LOGO, new String[] { "로고" } },
        { Asset.ICON, new String[] { "아이콘" } },
        { Asset.ART,  new String[] { "제목 옆", "옆 그림", "옆에 그림", "그림으로" } },
        { Asset.BG,   new String[] { "배경" } },
    };

    private static final Object[][] BLOCKS = {
        { Block.BENEFITS,  new String[] { "혜택" } },
        { Block.STEPS,     new String[] { "참여 방법", "참여방법", "참여 단계" } },
        { Block.CTA,       new String[] { "참여 버튼", "참여버튼", "신청 버튼", "신청버튼" } },
        { Block.HIGHLIGHT, new String[] { "강조" } },
        { Block.HERO,      new String[] { "제목", "소개", "맨 위", "상단" } },
    };

    private static final Pattern REMOVE = Pattern.compile("없애|빼\\b|빼 |빼줘|빼 줘|지워|삭제|제거");
    private static final Pattern LEFT = Pattern.compile("왼쪽|좌측|왼편");

    /** ★ 오른쪽은 기본 자리라 class 를 안 붙인다. "제목 옆 그림" 으로 읽는 단서로만 쓴다 */
    private static final Pattern RIGHT = Pattern.compile("오른쪽|우측|오른편|옆에|옆 ");

    /**
     * 요청문을 계획으로. <b>확실할 때만</b> 값을 돌려준다.
     *
     * ★ 역할을 못 찾으면 비켜선다. "1번 이미지 넣어줘" 는 어디에 넣을지 안 말한 것이고,
     *   그건 모델이 정하는 게 맞다.
     * ★ 번호를 못 찾았는데 빼는 요청도 아니면 비켜선다. 넣을 게 뭔지 모른다.
     * ★ 번호가 목록 밖이면 비켜선다 — 없는 이미지를 지어내 꽂지 않는다.
     *
     * @param assetCount 이 이벤트에 올라온 이미지 수
     */
    public static Optional<Plan> parse(String request, int assetCount) {
        return read(request, assetCount) instanceof Result.Ok ok ? Optional.of(ok.plan()) : Optional.empty();
    }

    /** {@link #parse} 와 같되, 막힌 조합을 {@link Result.CannotDo} 로 구분해 준다 */
    public static Result read(String request, int assetCount) {
        if (request == null || request.isBlank()) return new Result.NotMine();
        String t = request.toLowerCase(Locale.ROOT);

        // ★ 이미지 얘기라는 신호가 하나는 있어야 한다. 없으면 "배경" 이라는 말만 보고
        //   엉뚱한 요청("배경색을 바꿔줘")을 가로챈다
        if (!t.contains("이미지") && !t.contains("사진") && !t.contains("그림")) return new Result.NotMine();

        boolean remove = REMOVE.matcher(t).find();
        boolean left = LEFT.matcher(t).find();
        int index = findIndex(t);

        Asset role = findRole(t);
        // ★ 자리를 가리키는 말만 있어도 "제목 옆 그림" 으로 읽는다.
        //   "왼쪽에 1번 이미지를 추가해줘" 에는 역할 단어가 없지만 뜻은 또렷하다 —
        //   비켜서면 모델이 받아서 제목 위에 겹쳐 놓는다(실측).
        if (role == null && (left || RIGHT.matcher(t).find())) role = Asset.ART;

        // ★★ 빼는 요청은 역할이 없어도 된다. "1번 이미지 지워줘" 는 어디 있든 그걸 빼라는 뜻이다.
        //   비켜서면 라우터가 DELETE 로 읽어서 "hero 영역은 필수라 지울 수 없습니다" 가 나간다 —
        //   관리자는 이미지를 빼 달라고 했는데 영역을 지우려 한 것이다(실측).
        if (role == null && remove && index > 0) {
            if (index > assetCount) return new Result.CannotDo(missing(index, assetCount));
            return new Result.Ok(new Plan(index, null, null, true, false));
        }
        if (role == null) return new Result.NotMine();

        if (index == 0 && !remove) return new Result.NotMine();
        if (index > assetCount) return new Result.CannotDo(missing(index, assetCount));

        Block block = findBlock(t);
        // ★ 영역을 또렷하게 말했는데 그 역할을 못 쓰는 경우에만 막힌 것으로 본다.
        //   영역을 안 말했으면 기본 자리로 보내는 게 맞다 — 그건 막힌 게 아니다.
        if (block != null && !role.allowedOn(block)) return new Result.CannotDo(cannotDo(role, block));
        if (block == null) block = defaultBlock(role);
        if (!role.allowedOn(block)) return new Result.NotMine();

        return new Result.Ok(new Plan(index, role, block, remove, left));
    }

    /**
     * ★ 왜 안 되는지와 **대신 할 수 있는 것**을 같이 말한다.
     *   "안 됩니다" 만 들으면 관리자는 같은 말을 다르게 바꿔 가며 계속 시도한다.
     */
    private static String cannotDo(Asset role, Block block) {
        List<Asset> usable = Asset.of(block);
        String can = usable.isEmpty()
                ? "이 영역에는 이미지를 넣을 수 없어요."
                : "넣을 수 있는 건 " + usable.stream().map(Asset::desc)
                        .reduce((a, b) -> a + " · " + b).orElse("") + " 예요.";
        return shortName(block) + " 영역에는 " + role.desc() + "을(를) 쓸 수 없어요. " + can;
    }

    /**
     * ★ desc() 는 모델에게 주는 설명이라 한 문장이 길다. 관리자에게는 앞머리만 보여 준다 —
     *   EditService 의 삭제 · 요약 문구가 쓰는 방식과 같다.
     */
    private static String shortName(Block b) {
        return b.desc().split("[—.,]")[0].strip();
    }

    /**
     * 계획을 페이지에 적용한다.
     *
     * ★ 넣기 전에 **같은 역할의 기존 이미지를 뺀다.** 한 영역에 배경이 둘이면
     *   어느 쪽이 보이는지 아무도 모른다. 바꿔 끼우는 게 관리자가 기대하는 동작이다.
     * ★ 다른 역할은 건드리지 않는다. "2번을 배경으로" 가 1번 그림을 지우던 게 그 반대였다.
     * ★ 끝에 Asset.sanitize 를 돌린다 — 역할 · 중복 · 글자색 판정을 한 곳에서만 정한다.
     *
     * @return 바뀐 페이지 HTML. 바꿀 게 없었으면 원본 그대로
     */
    public static String apply(String pageHtml, Plan plan, List<Asset.Ref> assets) {
        Document doc = Jsoup.parseBodyFragment(pageHtml);

        // ★ 자리를 안 말하고 빼 달라는 요청 — 그 주소를 쓰는 이미지를 **페이지 전체에서** 뺀다.
        //   한 장을 배경과 그림 두 자리에 쓴 경우도 있으니 하나만 지우면 안 된다.
        if (plan.role() == null) {
            String url = assets.get(plan.index() - 1).url();
            boolean hit = false;
            for (Element img : doc.body().select("img[src=\"" + url + "\"]")) {
                img.remove();
                hit = true;
            }
            return hit ? doc.body().html() : pageHtml;
        }

        Element section = doc.selectFirst("section[data-block=\"" + plan.block().key() + "\"]");
        if (section == null) return pageHtml;

        // ★ 페이지 배경은 루트에 있을 수도 있다 — PageShell 이 거기로 옮겨 두기 때문이다.
        //   뺄 때는 양쪽을 다 봐야 한다.
        Element scope = plan.role() == Asset.PAGE ? doc.body() : section;
        for (Element old : scope.select("img." + plan.role().cssClass())) old.remove();

        if (plan.remove()) {
            Asset.sanitize(section, plan.block(), assets);
            return doc.body().html();
        }

        Asset.Ref ref = assets.get(plan.index() - 1);
        Element img = doc.createElement("img")
                .addClass(Asset.BASE)
                .addClass(plan.role().cssClass())
                .attr("src", ref.url())
                .attr("alt", ref.caption() == null ? "" : ref.caption());
        if (plan.left() && plan.role() == Asset.ART) img.addClass(Asset.PREFIX + "art-left");

        place(section, img, plan.role());
        Asset.sanitize(section, plan.block(), assets);
        return doc.body().html();
    }

    /**
     * 모델이 지운 이미지를 제자리에 되돌린다. <b>안전망이다.</b>
     *
     * ★ 왜 필요한가 — 수정 경로는 모델에게 영역 전체를 주고 전체를 돌려받는다.
     *   "전체 색을 초록색으로" 처럼 이미지와 무관한 요청에도 모델이 &lt;img&gt; 를 빼고 쓴다.
     *   checkPreserved 는 data-slot · id · class 만 지켜서 이걸 못 잡았다.
     *
     * ★ 되돌리는 게 맞는 이유 — **빼 달라는 요청은 여기까지 안 온다.**
     *   그건 parse 가 읽어서 모델 없이 처리한다. 이 경로에 오는 건 "이미지 얘기가 아닌
     *   요청" 이거나 "모호한 요청" 이고, 둘 다 이미지가 사라질 이유가 없다.
     *
     * ★ src 와 역할이 **둘 다** 같아야 "살아 있다" 로 본다. 모델이 역할만 바꿔 놓는
     *   경우(배경을 아이콘으로)가 있는데, 그건 되돌려야 할 손실이다.
     *
     * @return 되돌린 뒤의 HTML. 잃은 게 없으면 after 그대로
     */
    public static String restore(String before, String after, Block block) {
        if (before == null || before.isBlank() || after == null) return after;
        Document b = Jsoup.parseBodyFragment(before);
        var lost = b.select("img." + Asset.BASE);
        if (lost.isEmpty()) return after;

        Document a = Jsoup.parseBodyFragment(after);
        Element section = a.selectFirst("section[data-block]");
        if (section == null) return after;

        boolean changed = false;
        for (Element img : lost) {
            Asset role = roleOf(img);
            if (role == null) continue;
            String src = img.attr("src");
            if (!a.select("img." + role.cssClass() + "[src=\"" + src + "\"]").isEmpty()) continue;
            place(section, img.clone(), role);
            changed = true;
        }
        return changed ? a.body().html() : after;
    }

    private static Asset roleOf(Element img) {
        for (String c : img.classNames()) {
            var hit = Asset.find(c);
            if (hit.isPresent()) return hit.get();
        }
        return null;
    }

    /**
     * 영역 안 어디에 꽂을 것인가.
     *
     * ★ 자리 · 크기는 CSS 가 쥔다. 여기서 정하는 건 **문서 순서**뿐이다.
     *   그래도 순서를 맞춰 두면, CSS 가 아직 안 붙은 미리보기에서도 덜 망가진다.
     * ★ 아이콘은 이미 있는 아이콘 자리(이모지가 들어가는 span)를 쓴다. 자리가 없으면
     *   넣지 않는다 — 목록 밖에 아이콘 하나가 떠 있는 것보다 안 들어가는 편이 낫다.
     */
    private static void place(Element section, Element img, Asset role) {
        if (role == Asset.ICON) {
            Element holder = section.selectFirst(".benefit-icon, .step-icon");
            if (holder != null) {
                holder.empty();
                holder.appendChild(img);
            }
            return;
        }
        if (role == Asset.ART) {
            section.appendChild(img);
            return;
        }
        // 배경 · 로고 · 띠 · 페이지 배경 — 맨 앞
        section.prependChild(img);
    }

    private static Asset findRole(String t) {
        for (Object[] row : ROLES) {
            for (String w : (String[]) row[1]) {
                if (t.contains(w)) return (Asset) row[0];
            }
        }
        return null;
    }

    private static Block findBlock(String t) {
        for (Object[] row : BLOCKS) {
            for (String w : (String[]) row[1]) {
                if (t.contains(w)) return (Block) row[0];
            }
        }
        return null;
    }

    /** 영역을 안 말했을 때. 역할마다 가장 흔한 자리 하나다 */
    private static Block defaultBlock(Asset role) {
        return role == Asset.ICON ? Block.BENEFITS : Block.HERO;
    }

    private static String missing(int index, int assetCount) {
        return assetCount == 0
                ? "아직 올린 이미지가 없어요. 입력칸 옆 + 로 먼저 올려 주세요."
                : index + "번 이미지가 없어요. 지금 " + assetCount + "장 올라와 있어요.";
    }

    private static int findIndex(String t) {
        Matcher m = NUMBER.matcher(t);
        if (m.find()) return Integer.parseInt(m.group(1));
        for (int i = 0; i < ORDINALS.length; i++) {
            if (t.contains(ORDINALS[i] + " 번째") || t.contains(ORDINALS[i] + "번째")) return i + 1;
        }
        return 0;
    }
}
