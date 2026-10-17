package com.newvent.registry;

import java.util.Arrays;
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
    /**
     * @param seq 이벤트별 **고정 번호** (Asset.Ref.seq). 목록에서 몇 번째냐가 아니다.
     *            자리 이름만 말하고 빼 달라는 요청에서는 0 이다.
     */
    public record Plan(int seq, Asset role, Block block, boolean remove, boolean left) {}

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

    /**
     * ★★ 여기 없는 이름은 **못 알아듣고 기본 자리(hero)로 간다.**
     *   "3번 이미지를 쿠폰 배경으로" 가 hero 에 깔린 사고가 그거다(실측) —
     *   거절도 아니고 엉뚱한 자리에 들어가서 관리자는 왜 그런지 모른다.
     *   Block 에 영역을 더하면 여기도 같이 더한다.
     * ★ 순서가 중요하다. 긴 이름이 먼저다 — "참여 버튼" 이 "참여 방법" 보다 먼저 걸리면 안 된다.
     */
    private static final Object[][] BLOCKS = {
        { Block.STEPS,     new String[] { "참여 방법", "참여방법", "참여 단계", "참여단계" } },
        { Block.CTA,       new String[] { "참여 버튼", "참여버튼", "신청 버튼", "신청버튼", "참여하기" } },
        { Block.BENEFITS,  new String[] { "혜택" } },
        { Block.COUPON,    new String[] { "쿠폰" } },
        { Block.PRIZE,     new String[] { "경품", "상품" } },
        { Block.STATS,     new String[] { "숫자", "통계" } },
        { Block.SCHEDULE,  new String[] { "일정", "기간 안내" } },
        { Block.FAQ,       new String[] { "자주 묻는", "faq", "질문" } },
        { Block.AUDIENCE,  new String[] { "대상" } },
        { Block.COMPARE,   new String[] { "비교" } },
        { Block.NOTICES,   new String[] { "유의사항", "안내사항" } },
        { Block.HIGHLIGHT, new String[] { "강조" } },
        { Block.INTRO,     new String[] { "소개글" } },
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
    /**
     * 버전 이력에 남길 한 줄. 관리자가 읽는 글이라 class 이름이 새면 안 된다.
     *
     * ★★ role 을 **먼저** 본다. 자리를 안 말하고 뺀 경우에는 role · block 이 둘 다 null 이라,
     *   순서를 바꾸면 block.desc() 에서 터진다 — "1번 이미지 빼줘" 에 NPE 가 났다(실측).
     *   관리자에게는 "페이지를 고치는 중 문제가 생겼습니다" 만 보였다.
     */
    public static String summary(Plan plan) {
        if (plan.role() == null) return plan.seq() + "번 이미지 뺌";
        String where = plan.role() == Asset.PAGE
                ? plan.role().label()
                : shortName(plan.block()) + " " + plan.role().label();
        return (plan.remove() ? "이미지 뺌 — " : "이미지 넣음 — ") + where;
    }

    public static Optional<Plan> parse(String request, List<Asset.Ref> assets, String pageHtml) {
        return read(request, assets, pageHtml) instanceof Result.Ok ok
                ? Optional.of(ok.plan()) : Optional.empty();
    }

    /** {@link #parse} 와 같되, 막힌 조합을 {@link Result.CannotDo} 로 구분해 준다 */
    public static Result read(String request, List<Asset.Ref> assets, String pageHtml) {
        if (request == null || request.isBlank()) return new Result.NotMine();
        String t = request.toLowerCase(Locale.ROOT);
        String page = pageHtml == null ? "" : pageHtml;

        // ★★ 사진 위의 글자색은 CSS 가 쥐고 있다 — 모델이 class 를 아무리 붙여도 안 바뀐다.
        //   "문구 색을 어둡게 해줘" 가 v8 까지 가서도 흰색 그대로였던 자리다(실측).
        //   모델은 "강조함" 이라고 보고하고 화면은 그대로다 — 제일 나쁜 실패다.
        //   할 수 없다고 **먼저** 말하고, 어떻게 하면 되는지 같이 알려 준다.
        String colorLocked = textColorLocked(t, page);
        if (colorLocked != null) return new Result.CannotDo(colorLocked);

        boolean remove = REMOVE.matcher(t).find();
        boolean left = LEFT.matcher(t).find();

        Asset role = findRole(t);
        // ★ 자리를 가리키는 말만 있어도 "제목 옆 그림" 으로 읽는다.
        //   "왼쪽에 1번 이미지를 추가해줘" 에는 역할 단어가 없지만 뜻은 또렷하다 —
        //   비켜서면 모델이 받아서 제목 위에 겹쳐 놓는다(실측).
        if (role == null && (left || RIGHT.matcher(t).find())) role = Asset.ART;

        // ★★ "이미지" 라는 말이 없어도 받는 경우가 하나 있다 — **자리 이름을 말한 제거 요청**인데
        //   그 자리에 **실제로 사진이 깔려 있을 때**다.
        //   "페이지 전체 배경 없애줘" 가 세 번 연달아 모델로 넘어가서, 사진은 그대로인 채
        //   제목 문구와 테마와 레이아웃만 바뀐 사고가 났다(실측). 관리자는 세 번을 헛돌렸다.
        //
        // ★ 사진이 없으면 비켜선다. 그때 "배경 없애줘" 는 **배경색** 이야기일 수 있고,
        //   그건 모델이 맡는 게 맞다. 페이지를 보고 정하니 지어내지 않는다.
        // 자리 이름을 말한 제거 — 그 자리에 사진이 **실제로** 깔려 있을 때만
        boolean removeHere = remove && role != null && hasRole(page, role);
        // ★★ 페이지 전체 배경을 **까는** 요청도 받는다. "페이지 전체 배경으로 해줘" 에
        //   "이미지" 라는 말이 없어서 비켜섰고, 모델이 받아 hero 변형 목록만 내놨다(실측).
        // ★ 페이지 전체 배경만이다. 영역 배경(BG)은 색 · 그라데이션 이야기일 때가 많아서 못 받는다 —
        //   "hero 배경을 그라데이션으로" 를 사진 깔기로 읽으면 안 된다.
        //   반면 **페이지 전체 배경은 사진으로만 깐다**(ev-asset-page 가 유일한 길). 그래서 또렷하다.
        boolean putPageBg = !remove && role == Asset.PAGE;
        // ★ 겉모양 · 색 이야기가 섞이면 둘 다 비켜선다
        if (!saidImage(t) && (COLOR.matcher(t).find() || !(removeHere || putPageBg))) {
            return new Result.NotMine();
        }

        int seq = findSeq(t, assets);

        // ★★ 빼는 요청은 역할이 없어도 된다. "1번 이미지 지워줘" 는 어디 있든 그걸 빼라는 뜻이다.
        //   비켜서면 라우터가 DELETE 로 읽어서 "hero 영역은 필수라 지울 수 없습니다" 가 나간다 —
        //   관리자는 이미지를 빼 달라고 했는데 영역을 지우려 한 것이다(실측).
        if (role == null && remove && seq != 0) {
            if (refOf(assets, seq) == null) return new Result.CannotDo(missing(seq, assets));
            return new Result.Ok(new Plan(seq, null, null, true, false));
        }
        // ★★ "이미지 없애줘" — 번호도 자리도 안 말했다. 페이지에 사진이 **딱 한 장**이면
        //   모호하지 않다. 비켜섰더니 라우터가 "요청을 이해하지 못했습니다" 를 냈다(실측).
        if (role == null && remove && seq == 0) {
            List<Asset.Ref> inPage = inPage(page, assets);
            if (inPage.size() == 1) {
                return new Result.Ok(new Plan(inPage.get(0).seq(), null, null, true, false));
            }
            // ★ 두 장 이상이면 어느 것인지 모른다. 지어내지 말고 **되묻는다** —
            //   모델에게 넘기면 "요청을 이해하지 못했습니다" 만 나온다
            if (inPage.size() > 1) return new Result.CannotDo(ambiguous(inPage));
        }
        // ★★ 말한 자리에 사진이 없을 때. "배경이미지 없애줘" 가 페이지 전체 배경을 못 찾고
        //   "그 자리에는 이미지가 없어요" 를 냈다(실측) — 사진은 루트에 있고 "배경" 은 영역 배경이다.
        //   관리자가 자리 이름을 정확히 외울 필요는 없다. 한 곳뿐이면 거기가 거기다.
        if (remove && role != null && !hasRole(page, role)) {
            List<Asset> here = rolesInPage(page);
            if (here.isEmpty()) return new Result.CannotDo("페이지에 넣은 이미지가 없어요.");

            // ★★ 영역 배경 ↔ 페이지 전체 배경만 바꿔 읽는다. 관리자가 "배경" 이라고 할 때
            //   둘 중 어느 쪽인지는 **페이지가 안다** — 하나뿐이면 그게 그거다.
            // ★ 다른 자리로는 안 넘어간다. "로고 빼줘" 를 배경 빼기로 읽으면 안 된다 —
            //   로고라고 또렷하게 말한 요청이다.
            if (isBackground(role) && here.stream().filter(AssetEdit::isBackground).count() == 1) {
                role = here.stream().filter(AssetEdit::isBackground).findFirst().orElseThrow();
            } else {
                // ★ 지어내지 않는다. **어디에 있는지** 알려 주고 고르게 한다
                return new Result.CannotDo(notHere(role, here));
            }
        }

        if (role == null) return new Result.NotMine();

        // ★★ 번호를 안 말했다. 올린 게 **한 장뿐이면** 그게 그거다 — 고를 게 없다.
        //   "이미지를 페이지 전체 배경으로 해줘" 를 비켜섰더니 모델이 받아서 실패했다(실측).
        // ★ 여러 장이면 지어내지 않는다. 대신 **번호를 물어본다** — 모델에게 넘기면
        //   엉뚱한 걸 고르거나 "반영하지 못했습니다" 만 나온다.
        if (seq == 0 && !remove) {
            if (assets.isEmpty()) return new Result.CannotDo(missing(0, assets));
            if (assets.size() == 1) seq = assets.get(0).seq();
            else return new Result.CannotDo(ambiguous(assets));
        }
        if (seq != 0 && refOf(assets, seq) == null) {
            return new Result.CannotDo(missing(seq, assets));
        }

        Block block = findBlock(t);
        // ★ 영역을 또렷하게 말했는데 그 역할을 못 쓰는 경우에만 막힌 것으로 본다.
        //   영역을 안 말했으면 기본 자리로 보내는 게 맞다 — 그건 막힌 게 아니다.
        if (block != null && !role.allowedOn(block)) return new Result.CannotDo(cannotDo(role, block));
        if (block == null) block = defaultBlock(role);
        if (!role.allowedOn(block)) return new Result.NotMine();

        return new Result.Ok(new Plan(seq, role, block, remove, left));
    }

    /**
     * ★ 왜 안 되는지와 **대신 할 수 있는 것**을 같이 말한다.
     *   "안 됩니다" 만 들으면 관리자는 같은 말을 다르게 바꿔 가며 계속 시도한다.
     */
    private static String cannotDo(Asset role, Block block) {
        List<Asset> usable = Asset.of(block);
        String can = usable.isEmpty()
                ? "이 영역에는 이미지를 넣을 수 없어요."
                : "넣을 수 있는 건 " + usable.stream().map(Asset::label)
                        .reduce((a, b) -> a + " · " + b).orElse("") + " 예요.";
        return shortName(block) + " 영역에는 " + role.label() + "을 쓸 수 없어요. " + can;
    }

    /**
     * ★ desc() 는 모델에게 주는 설명이라 한 문장이 길다. 관리자에게는 앞머리만 보여 준다 —
     *   EditService 의 삭제 · 요약 문구가 쓰는 방식과 같다.
     */
    /**
     * 영역의 **사람이 읽는** 짧은 이름. 버전 이력과 거절 문구가 같이 쓴다.
     *
     * ★ key() 를 그대로 쓰면 안 된다 — 버전 이력에 "hero ev-asset-bg" 가 찍혀서
     *   관리자가 CSS class 를 읽게 됐다(실측).
     */
    public static String shortName(Block b) {
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
            Asset.Ref target = refOf(assets, plan.seq());
            if (target == null) return pageHtml;
            String url = target.url();
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

        Asset.Ref ref = refOf(assets, plan.seq());
        if (ref == null) return pageHtml;
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
        // ★★ 말 순서를 안 가린다. "배경 전체로 깔아줘" 가 표에 없어서 영역 배경으로 떨어졌다(실측) —
        //   관리자는 페이지 전체를 말했는데 hero 한 칸에만 깔렸다.
        //   "전체" 와 "배경" 이 같이 있으면 페이지 전체 배경이다. 사이에 뭐가 끼든 상관없다.
        if (t.contains("전체") && t.contains("배경")) return Asset.PAGE;
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

    /**
     * ★★ 몇 장인지가 아니라 **번호를 다 적어 준다.** 번호에 구멍이 생기므로
     *   ("1 · 3 · 4") "3장 있어요" 만으로는 관리자가 무엇을 불러야 할지 모른다.
     */
    /** 번호를 안 말했는데 올린 게 여러 장일 때 — 되묻는다. 모델에게 넘기지 않는다 */
    private static String ambiguous(List<Asset.Ref> assets) {
        return "몇 번 이미지인지 알려 주세요. 지금 있는 건 " + numbersOf(assets) + " 이에요.";
    }

    private static String numbersOf(List<Asset.Ref> assets) {
        return assets.stream().map(r -> r.seq() + "번")
                .reduce((a, b) -> a + " · " + b).orElse("");
    }

    private static String missing(int seq, List<Asset.Ref> assets) {
        if (assets.isEmpty()) return "아직 올린 이미지가 없어요. 입력칸 옆 + 로 먼저 올려 주세요.";
        String head = seq < 0 ? "말씀하신 순서의 이미지가 없어요." : seq + "번 이미지가 없어요.";
        return head + " 지금 있는 건 " + numbersOf(assets) + " 이에요.";
    }

    /**
     * 요청문이 가리킨 이미지의 **고정 번호**. 아무 번호도 안 말했으면 0.
     *
     * ★ "3번" 은 그대로 3 이다 — 화면의 배지에 적힌 번호이자 행의 seq 다.
     * ★★ "세번째" 는 다르다. **보이는 순서**라서 목록의 세 번째가 가진 seq 로 바꾼다.
     *   번호에 구멍이 있으면(1 · 3 · 4) "세번째" 와 "3번" 은 다른 사진이다.
     * ★ 목록보다 뒤를 말했으면 -1 — 없는 것으로 보고 거절 문구를 띄운다.
     */
    private static int findSeq(String t, List<Asset.Ref> assets) {
        Matcher m = NUMBER.matcher(t);
        if (m.find()) return Integer.parseInt(m.group(1));
        for (int i = 0; i < ORDINALS.length; i++) {
            if (t.contains(ORDINALS[i] + " 번째") || t.contains(ORDINALS[i] + "번째")) {
                return i < assets.size() ? assets.get(i).seq() : -1;
            }
        }
        return 0;
    }

    /**
     * 색 · 겉모양 이야기 — "이미지" 라는 말 없이 자리 이름만 보고 가로채면 안 되는 신호.
     *
     * ★ 색 이름까지 적는다. "페이지 배경을 연한 초록으로" 에는 "색" 이라는 글자가 없다.
     */
    private static final Pattern COLOR = Pattern.compile(
            "색|컬러|팔레트|그라데이션|초록|파랑|빨강|노랑|보라|분홍|검정|하양|흰|회색|남색|주황|베이지|투명");

    private static final Pattern TEXT_COLOR = Pattern.compile(
            "(글자|문구|제목|텍스트|글씨)[^.]{0,12}(색|컬러)|(색|컬러)[^.]{0,8}(글자|문구|제목|텍스트|글씨)");

    /**
     * 사진 때문에 글자색이 잠겨 있나. 잠겼으면 그 이유를, 아니면 null.
     *
     * ★ 잠그는 건 어두운 사진(on-dark) · 섞인 사진(on-mixed) · 페이지 전체 배경뿐이다.
     *   밝은 그림(on-light) 위에서는 테마 글자색이 그대로 살아 있어서 색을 바꿀 수 있다.
     */
    private static String textColorLocked(String t, String page) {
        if (!TEXT_COLOR.matcher(t).find()) return null;
        boolean locked = page.contains(Asset.PREFIX + "on-dark")
                || page.contains(Asset.PREFIX + "on-mixed")
                || page.contains(Asset.PAGE.cssClass());
        if (!locked) return null;
        return "사진 위의 글자색은 읽히는 쪽으로 자동으로 정해져요 — 직접 고를 수 없어요. "
                + "색을 바꾸고 싶으면 먼저 \"배경 이미지 없애줘\" 로 사진을 빼 주세요.";
    }

    /** 이미지 얘기라는 말이 하나라도 있나 */
    private static boolean saidImage(String t) {
        return t.contains("이미지") || t.contains("사진") || t.contains("그림");
    }

    /**
     * 페이지에 그 배치의 사진이 실제로 있나.
     *
     * ★ 파싱하지 않는다. 배치 이름은 class 로만 쓰이고 본문에 나올 일이 없다 —
     *   ev-asset-bg 같은 문자열이 글에 들어 있으면 그건 이미 다른 문제다.
     */
    private static boolean hasRole(String pageHtml, Asset role) {
        return pageHtml.contains(role.cssClass());
    }

    /** 영역 배경 · 페이지 전체 배경 — 관리자가 둘 다 "배경" 이라고 부른다 */
    private static boolean isBackground(Asset a) {
        return a == Asset.BG || a == Asset.PAGE;
    }

    /** 지금 페이지에 사진이 들어가 있는 자리들 */
    private static List<Asset> rolesInPage(String pageHtml) {
        return Arrays.stream(Asset.values()).filter(a -> hasRole(pageHtml, a)).toList();
    }

    private static String notHere(Asset role, List<Asset> here) {
        return role.label() + "에는 이미지가 없어요. 지금 이미지가 있는 곳은 "
                + here.stream().map(Asset::label).reduce((a, b) -> a + " · " + b).orElse("")
                + " 이에요.";
    }

    /** 지금 페이지에 실제로 들어가 있는 사진들 */
    private static List<Asset.Ref> inPage(String pageHtml, List<Asset.Ref> assets) {
        return assets.stream().filter(r -> pageHtml.contains(r.url())).toList();
    }

    /** 그 번호의 이미지. 없으면 null */
    private static Asset.Ref refOf(List<Asset.Ref> assets, int seq) {
        return assets.stream().filter(r -> r.seq() == seq).findFirst().orElse(null);
    }
}
