package com.newvent.golden;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.yaml.snakeyaml.Yaml;

import com.newvent.editor.service.RawRoute;
import com.newvent.editor.service.RouteParser;
import com.newvent.generation.service.HtmlPolicy;
import com.newvent.infra.llm.BedrockClient;
import com.newvent.infra.llm.LlmClient;
import com.newvent.registry.Block;
import com.newvent.registry.BlockValidator;
import com.newvent.registry.BlockValidator.Failure;
import com.newvent.registry.Palette;
import com.newvent.registry.PromptBuilder;
import com.newvent.registry.Theme;

/**
 * 생성 · 수정 골든 셋 — <b>모델이 하는 일만</b> 잰다.
 *
 * <h3>범위를 좁힌 이유</h3>
 * 서버가 결정하는 부분(이미지 배치 · 블록 삭제 · 개인정보 필터 · 지원 안 되는 색)은
 * 모델이 필요 없고 <b>이미 단위 테스트가 있다.</b>
 * <ul>
 *   <li>{@code AssetEditTest} 50개 — 없는 번호 · 업로드 없음 · 여러 장이면 되묻기 ·
 *       "이미지만 들어가고 문구는 한 글자도 안 바뀐다"</li>
 *   <li>{@code EditServiceTest} — "삭제는 모델을 부르지 않고 섹션만 걷어낸다" ·
 *       "필수 영역 삭제는 거절한다" (라우터를 고정해 돌리는 저비용 테스트)</li>
 * </ul>
 * 같은 동작을 여기서 다시 보면 <b>느리고 비싸고 불안정하게</b> 재는 꼴이라 넣지 않았다.
 *
 * <h3>그래서 재는 것은 둘뿐</h3>
 * <ol>
 *   <li><b>라우터 분류</b> — 기준선({@code routerMinPercent}) 미달이면 <b>테스트가 실패한다.</b>
 *       분류는 HTML 생성보다 안정적이라 기준선을 걸어도 잘 흔들리지 않는다.</li>
 *   <li><b>생성 · 수정 HTML 품질</b> — 숫자를 보고한다. 단정하지 않는다.
 *       모델 출력이 매번 달라 단정을 걸면 실패가 잦아 아무도 안 돌리게 된다.</li>
 * </ol>
 *
 * <h3>★★ CI 에서 돌지 않는다 — 돈이 나가기 때문이다</h3>
 * <pre>
 *   GOLDEN_SET=1 LLM_PROVIDER=bedrock ./gradlew test --tests "*GoldenSetEvalTest" --info
 * </pre>
 * 재시도는 하지 않는다. 보고 싶은 건 <b>한 번에 제대로 나오는 비율</b>이다.
 */
@EnabledIfEnvironmentVariable(named = "GOLDEN_SET", matches = "1")
@DisplayName("골든 셋 — 라우터 분류(단정)와 HTML 품질(보고)")
class GoldenSetEvalTest {

    private static final Pattern THEME = Pattern.compile(Theme.PREFIX + "[a-z0-9-]+");
    private static final Pattern PALETTE = Pattern.compile(Palette.PREFIX + "[a-z0-9-]+");

    private LlmClient client;
    private int calls;
    private long inTokens, outTokens;

    private final List<String> report = new ArrayList<>();
    private final Map<String, Integer> failureHistogram = new TreeMap<>();

    // ── 실행 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("라우터는 기준선을 넘어야 하고, HTML 품질은 숫자로 남긴다")
    void 골든셋() throws Exception {
        Map<String, Object> doc = loadCases();
        client = newClient();

        // ★★ 먼저 한 번만 불러 본다.
        //   연결이 안 되는 상태로 39번을 돌면 전부 0점이 나오고 원인은 안 보인다.
        //   여기서 멈추면 토큰도 안 쓰고 진짜 이유가 그대로 드러난다.
        preflight();

        // ① 라우터 — 단정한다
        int routerPass = 0, routerTotal = 0;
        head("라우터 분류 (기준선 " + routerMin(doc) + "%)");
        for (Map<String, Object> c : list(doc.get("router"))) {
            routerTotal++;
            Scored s = runRouter(c);
            if (s.ok) routerPass++;
            row(s.ok, shorten(str(c.get("request"))), s.notes);
        }

        // ②' 판정기 — "이게 페이지 수정 요청인가" 는 라우터가 아니라 editJudge 가 본다
        int judgePass = 0, judgeTotal = 0;
        head("판정기 (수정 요청이 아닌 입력을 거르나)");
        for (Map<String, Object> c : list(doc.get("judge"))) {
            judgeTotal++;
            Scored s = runJudge(c);
            if (s.ok) judgePass++;
            row(s.ok, shorten(str(c.get("request"))), s.notes);
        }

        // ② 생성 — 보고만
        Map<String, String> pages = new LinkedHashMap<>();
        int genPass = 0, genTotal = 0;
        head("생성 품질");
        for (Map<String, Object> c : list(doc.get("generate"))) {
            genTotal++;
            GenResult r = runGenerate(c);
            if (r.html != null) pages.put(str(c.get("name")), r.html);
            if (r.ok) genPass++;
            row(r.ok, str(c.get("name")), r.notes);
        }

        // ③ 수정 — 보고만
        int editPass = 0, editTotal = 0;
        head("수정 품질");
        for (Map<String, Object> c : list(doc.get("edit"))) {
            editTotal++;
            String base = pages.get(str(c.get("base")));
            if (base == null) {
                row(false, str(c.get("name")),
                        List.of("기준 페이지(" + c.get("base") + ") 생성이 실패해 건너뜀"));
                continue;
            }
            Scored s = runEdit(c, base);
            if (s.ok) editPass++;
            row(s.ok, str(c.get("name")), s.notes);
        }

        summary(routerPass, routerTotal, judgePass, judgeTotal, genPass, genTotal, editPass, editTotal);
        print();

        // ★★ 여기만 단정한다. 라우터 분류가 무너지면 수정 기능 전체가 멈춘다 —
        //   요청이 엉뚱한 블록으로 가거나 조용히 되묻기로 빠진다.
        int pct = pct(routerPass, routerTotal);
        assertTrue(pct >= routerMin(doc),
                "라우터 분류 " + pct + "% — 기준선 " + routerMin(doc) + "% 아래입니다. "
                        + "표본이 " + routerTotal + "개라 한두 건이 " + (100 / Math.max(1, routerTotal))
                        + "%p 를 움직입니다. 위 표에서 실패한 문장과, 요약의 모델·프롬프트 판본을 "
                        + "지난번 실행과 비교해 모델 변동인지 회귀인지 가르세요.");
    }

    /** 호출이 되는 상태인지 한 번만 확인한다. 안 되면 진짜 이유를 그대로 띄우고 멈춘다 */
    private void preflight() {
        try {
            LlmClient.Response r = client.chat(
                    LlmClient.Request.router("다음 요청을 JSON 으로 분류해라.", "안녕"));
            System.out.println("[골든셋] 연결 확인 — " + client.providerName() + " / " + client.modelName()
                    + " · 입력 " + r.inputTokens() + " · 출력 " + r.outputTokens());
            inTokens += r.inputTokens();
            outTokens += r.outputTokens();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "모델에 연결하지 못했습니다. 나머지 38번은 돌리지 않습니다.\n"
                    + "  provider : " + client.providerName() + "\n"
                    + "  model    : " + client.modelName() + "\n"
                    + "  region   : " + env("AWS_REGION", "ap-northeast-2") + "\n"
                    + "  원인     : " + why(e) + "\n"
                    + "  흔한 이유 : ① 로컬에 AWS 자격증명이 없음 (aws sts get-caller-identity 로 확인)\n"
                    + "             ② 그 리전에서 모델 접근이 열려 있지 않음\n"
                    + "             ③ 모델 아이디가 다름 (LLM_MODEL 로 바꿀 수 있음)", e);
        }
    }

    /** 예외의 진짜 이유 — 가장 안쪽 원인까지 따라간다 */
    private static String why(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    // ── 라우터 ──────────────────────────────────────────────────────────────

    private record Scored(boolean ok, List<String> notes) {}

    private Scored runRouter(Map<String, Object> c) {
        String request = str(c.get("request"));
        String expect = str(c.get("expect"));          // null · ASK_BACK
        List<String> notes = new ArrayList<>();
        calls = 0;

        LlmClient.Response res;
        try {
            res = chat(LlmClient.Request.router(PromptBuilder.router(), request.strip()));
        } catch (RuntimeException e) {
            return new Scored(false, List.of("호출 실패: " + why(e)));
        }

        Optional<List<RawRoute>> parsed = RouteParser.parse(res.content());
        boolean askBack = parsed.isEmpty() || parsed.get().isEmpty();

        if ("ASK_BACK".equals(expect)) {
            if (!askBack) {
                notes.add("되묻기를 기대했는데 작업을 냄: " + describe(parsed.get()));
                bump("ROUTER_OVERREACH");
                return new Scored(false, notes);
            }
            return new Scored(true, notes);
        }
        if (askBack) {
            notes.add("작업을 못 냄 (되묻기로 빠짐)");
            bump("ROUTER_PARSE");
            return new Scored(false, notes);
        }

        List<RawRoute> ops = parsed.get();
        boolean ok = true;

        Integer maxOps = asInt(c.get("maxOps"));
        if (maxOps != null && ops.size() > maxOps) {
            // ★ 상한을 넘기면 서버가 응답을 통째로 버린다 = 조용한 되묻기
            notes.add("작업 " + ops.size() + "개 (상한 " + maxOps + ") — 버려짐");
            bump("ROUTER_TOO_MANY_OPS");
            ok = false;
        }
        String wantTarget = str(c.get("expectTarget"));
        if (wantTarget != null && ops.stream().noneMatch(o -> wantTarget.equals(o.target()))) {
            notes.add("영역: " + targets(ops) + " (기대 " + wantTarget + ")");
            ok = false;
        }
        for (String t : strs(c.get("expectTargets"))) {
            if (ops.stream().noneMatch(o -> t.equals(o.target()))) {
                notes.add("빠진 영역: " + t);
                ok = false;
            }
        }
        String wantOp = str(c.get("expectOp"));
        if (wantOp != null && ops.stream().noneMatch(o -> wantOp.equalsIgnoreCase(o.op()))) {
            notes.add("연산: " + opsOf(ops) + " (기대 " + wantOp + ")");
            ok = false;
        }
        String forbidOp = str(c.get("forbidOp"));
        if (forbidOp != null && ops.stream().anyMatch(o -> forbidOp.equalsIgnoreCase(o.op()))) {
            notes.add("나오면 안 되는 연산: " + forbidOp);
            ok = false;
        }
        // ★ 여기까지는 "기대한 것이 들어 있나" 만 본다. 그것만으로는
        //   요청하지 않은 영역까지 건드리는 응답을 못 잡는다.
        //   exactTargets 를 적은 케이스는 **집합이 정확히 같은지** 본다.
        List<String> exact = strs(c.get("exactTargets"));
        if (!exact.isEmpty()) {
            List<String> actual = ops.stream().map(RawRoute::target).distinct().sorted().toList();
            List<String> want = exact.stream().distinct().sorted().toList();
            if (!actual.equals(want)) {
                notes.add("영역 집합: " + actual + " (기대 정확히 " + want + ")");
                bump("ROUTER_EXTRA_TARGET");
                ok = false;
            }
        }
        return new Scored(ok, notes);
    }

    /**
     * 판정기 — "이 요청이 페이지 수정 요청인가".
     *
     * ★★ 전에는 "안녕" · "오늘 날씨 어때?" 를 라우터 섹션에서 쟀다. 잘못이었다.
     *   실제 EditService 는 라우터보다 먼저 {@code editJudge} 로 이걸 거른다.
     *   라우터는 "수정 요청이 맞다" 는 전제에서 영역만 고르는 부품이라,
     *   엉뚱한 입력에도 뭔가를 고르는 게 자연스럽다. 탓할 부품을 잘못 고른 셈이다.
     */
    private Scored runJudge(Map<String, Object> c) {
        String request = str(c.get("request"));
        boolean wantKnown = !"ASK_BACK".equals(str(c.get("expect")));
        calls = 0;
        try {
            LlmClient.Response res = chat(LlmClient.Request.router(
                    PromptBuilder.editJudge(), PromptBuilder.editJudgeUser(request)));
            Boolean known = res.truncated() ? null
                    : com.newvent.generation.service.RequestVagueness.parseKnown(res.content());
            if (known == null) {
                bump("JUDGE_PARSE");
                // ★ 판정기 장애가 수정을 막지 않게 되어 있다 — 실패가 아니라 통과로 흐른다
                return new Scored(!wantKnown ? false : true, List.of("판정 불가 (장애 시 통과로 흐름)"));
            }
            if (known != wantKnown) {
                return new Scored(false, List.of(
                        "판정: " + (known ? "수정 요청" : "수정 요청 아님")
                                + " (기대 " + (wantKnown ? "수정 요청" : "수정 요청 아님") + ")"));
            }
            return new Scored(true, List.of());
        } catch (RuntimeException e) {
            return new Scored(false, List.of("호출 실패: " + why(e)));
        }
    }

    // ── 생성 ────────────────────────────────────────────────────────────────

    private record GenResult(boolean ok, String html, List<String> notes) {}

    private GenResult runGenerate(Map<String, Object> c) {
        String request = str(c.get("request"));
        String title = str(c.get("title"));
        String period = str(c.get("period"));
        List<String> notes = new ArrayList<>();
        calls = 0;

        LlmClient.Response res;
        try {
            res = chat(LlmClient.Request.html(PromptBuilder.generate(request), request));
        } catch (RuntimeException e) {
            return new GenResult(false, null, List.of("호출 실패: " + why(e)));
        }

        HtmlPolicy policy = HtmlPolicy.generation(request, title, period);
        String html = policy.clean(res.content());
        List<Failure> failures = policy.validate(html);
        failures.forEach(f -> bump(f.kind().name()));

        boolean ok = true;
        String text = textOf(html);

        for (String key : strs(c.get("expectBlocks"))) {
            if (!hasBlock(html, key)) { notes.add("없음: " + key); ok = false; }
        }
        for (String key : strs(c.get("forbidBlocks"))) {
            if (hasBlock(html, key)) { notes.add("과생성: " + key); ok = false; }
        }
        // ★ 블록만 있고 내용이 비면 소용없다 — 요청한 말이 실제로 들어갔는지 본다
        for (String want : strs(c.get("expectContains"))) {
            if (!text.contains(want)) { notes.add("문구 빠짐: " + want); ok = false; }
        }
        for (String no : strs(c.get("forbidContains"))) {
            if (text.contains(no)) { notes.add("지어낸 문구: " + no); ok = false; }
        }
        List<String> themes = strs(c.get("expectThemeIn"));
        if (!themes.isEmpty()) {
            String picked = first(THEME, html);
            if (picked == null || themes.stream().noneMatch(picked::contains)) {
                notes.add("분위기: " + picked + " (기대 " + themes + ")");
                ok = false;
            }
        }
        List<String> paletteWords = strs(c.get("expectPaletteContains"));
        if (!paletteWords.isEmpty()) {
            String picked = first(PALETTE, html);
            if (picked == null || paletteWords.stream().noneMatch(picked::contains)) {
                notes.add("색: " + picked + " (기대 " + paletteWords + " 중 하나)");
                ok = false;
            }
        }
        for (String code : strs(c.get("forbidFailures"))) {
            if (failures.stream().anyMatch(f -> f.kind().name().equals(code))) {
                notes.add("실패코드: " + code);
                ok = false;
            }
        }
        if (failures.stream().anyMatch(Failure::isBlocking)) {
            notes.add("재시도 유발: " + names(failures));
            ok = false;
        }
        return new GenResult(ok, html, notes);
    }

    // ── 수정 ────────────────────────────────────────────────────────────────

    private Scored runEdit(Map<String, Object> c, String base) {
        String request = str(c.get("request"));
        List<String> notes = new ArrayList<>();
        boolean ok = true;
        calls = 0;

        // ★ 라우터 분류 자체는 위 router 섹션이 잰다. 여기서는 수정 결과만 본다.
        //   그래서 대상 블록을 YAML 이 지정하면 그걸 쓰고, 없으면 라우터에게 묻는다.
        Block block;
        String wantTarget = str(c.get("expectTarget"));
        if (wantTarget != null) {
            Optional<Block> b = Block.find(wantTarget);
            if (b.isEmpty()) return new Scored(false, List.of("YAML 의 expectTarget 이 없는 블록: " + wantTarget));
            block = b.get();
        } else {
            // 페이지 전체 색·분위기 요청 — 클래스는 hero 에 붙었다가 루트로 올라간다
            block = Block.of("hero");
        }

        String before = BlockValidator.blockOf(base, block);
        if (before == null || before.isBlank()) {
            return new Scored(false, List.of("기준 페이지에 " + block.key() + " 영역이 없음"));
        }

        String op = c.get("op") == null ? "EDIT" : str(c.get("op"));
        boolean addItem = "ADD".equalsIgnoreCase(op);
        boolean looksRequest = "STYLE".equalsIgnoreCase(op);   // ★ 안 넘기면 STYLE 이 EDIT 과 같은 프롬프트로 나간다

        String system = PromptBuilder.edit(block, isTemplateBlock(before), true, looksRequest,
                addItem, List.of(), request, false, false);

        // ★★ 여기가 핵심이다. 전에는 user 프롬프트에 요청문만 보냈다 —
        //   모델이 **고칠 HTML 을 못 본 채** 빈손에서 새로 썼고, 그래서 id 를 지어내고
        //   기존 항목을 통째로 날렸다. 실제 EditService.userPrompt 와 같은 모양으로 맞춘다.
        String user = editUserPrompt(request, before, op, block);

        LlmClient.Response edited;
        try {
            edited = chat(LlmClient.Request.html(system, user));
        } catch (RuntimeException e) {
            return new Scored(false, List.of("호출 실패: " + why(e)));
        }

        HtmlPolicy policy = HtmlPolicy.edit(block, before, request, addItem, List.of());
        String after = policy.clean(edited.content());
        List<Failure> failures = policy.validate(after);
        failures.forEach(f -> bump(f.kind().name()));

        for (String code : strs(c.get("forbidFailures"))) {
            if (failures.stream().anyMatch(f -> f.kind().name().equals(code))) {
                notes.add("실패코드: " + code);
                ok = false;
            }
        }
        if (failures.stream().anyMatch(Failure::isBlocking)) {
            notes.add("재시도 유발: " + names(failures));
            ok = false;
        }

        Integer delta = asInt(c.get("itemDelta"));
        if (delta != null) {
            int actual = itemTexts(after).size() - itemTexts(before).size();
            if (actual != delta) { notes.add("항목 증감: " + actual + " (기대 " + delta + ")"); ok = false; }
        }
        // ★ 기존 항목이 살아 있나 — 항목 수만 맞고 내용이 갈아엎이면 소용없다.
        //   본문 전체에서 찾으면 같은 말이 다른 항목에 있어도 통과하므로 **항목끼리 1:1** 로 맞춘다.
        if (Boolean.TRUE.equals(c.get("keepItems"))) {
            List<String> lost = lostItems(before, after);
            if (!lost.isEmpty()) {
                notes.add("사라진 항목 " + lost.size() + "개: " + shorten(lost.get(0)));
                ok = false;
            }
        }
        String afterText = textOf(after);
        for (String want : strs(c.get("expectContains"))) {
            if (!afterText.contains(want)) { notes.add("문구 빠짐: " + want); ok = false; }
        }
        for (String no : strs(c.get("forbidContains"))) {
            if (afterText.contains(no)) { notes.add("지어낸 문구: " + no); ok = false; }
        }
        if (Boolean.TRUE.equals(c.get("keepPalette")) && changed(PALETTE, before, after)) {
            notes.add("색이 바뀜 — 요청에 없었다"); ok = false;
        }
        if (Boolean.TRUE.equals(c.get("keepTheme")) && changed(THEME, before, after)) {
            notes.add("분위기가 바뀜 — 요청에 없었다"); ok = false;
        }
        if (Boolean.TRUE.equals(c.get("expectPaletteChanged")) && !changed(PALETTE, before, after)) {
            notes.add("색이 안 바뀜"); ok = false;
        }
        if (Boolean.TRUE.equals(c.get("expectThemeChanged")) && !changed(THEME, before, after)) {
            notes.add("분위기가 안 바뀜"); ok = false;
        }
        return new Scored(ok, notes);
    }

    /**
     * 실제 {@code EditService.userPrompt} 와 같은 모양의 user 프롬프트.
     *
     * ★★ 모델에게 <b>고칠 HTML 을 반드시 준다.</b> 안 주면 빈손에서 새로 써서
     *   id 를 지어내고(ID_INVENTED) 기존 항목을 날린다(VALUE_REMOVED) — 실측으로 수정 0/9 였다.
     */
    private static String editUserPrompt(String request, String before, String op, Block block) {
        StringBuilder s = new StringBuilder();
        s.append("[관리자 요청]\n").append(request.strip()).append("\n\n");
        if ("ADD".equalsIgnoreCase(op) && !before.isBlank()) {
            s.append("[이번 요청은 **항목 하나를 더하는 것**이다]\n")
             .append("- 이 영역의 목록에 항목을 **하나만** 더한다. 기존 항목은 그대로 둔다.\n")
             .append("- 더하는 내용은 위 관리자 문구를 쓴다. 없는 수치를 지어내지 마라.\n");
            if (block == Block.of("benefits")) {
                s.append("- 요청한 혜택만 하나 더한다. 요청에 없는 다른 혜택은 만들지 마라.\n");
            }
            s.append("\n");
        }
        s.append("[현재 내용 — 이 영역을 수정한다]\n").append(before).append("\n");
        return s.toString();
    }

    /** 템플릿에서 온 블록인가 — 프롬프트가 달라진다 */
    private static boolean isTemplateBlock(String before) {
        if (before == null || before.isBlank()) return false;
        Element root = Jsoup.parseBodyFragment(before).body().selectFirst("section[data-block]");
        return root != null && root.hasClass("ev-block");
    }

    // ── 보조 ────────────────────────────────────────────────────────────────

    private boolean hasBlock(String html, String key) {
        return Block.find(key)
                .map(b -> Jsoup.parseBodyFragment(html).body().selectFirst(b.selector()) != null)
                .orElse(false);
    }

    /** 태그를 걷어낸 본문 — 문구가 들어갔는지 볼 때 쓴다 */
    private static String textOf(String html) {
        return html == null ? "" : Jsoup.parseBodyFragment(html).text();
    }

    /**
     * 항목 하나하나의 글 — 수를 세고, 보존됐는지 본다.
     *
     * ★ 중첩을 걷어낸다. {@code .card} 안에 {@code li} 가 들어 있으면 선택자가 둘 다 잡아
     *   같은 항목을 두 번 세고 itemDelta 가 실제와 어긋난다. 조상이 이미 뽑힌 노드는 버린다.
     */
    private static List<String> itemTexts(String html) {
        Document d = Jsoup.parseBodyFragment(html == null ? "" : html);
        Elements all = d.body().select("li, [data-item], .card");
        return all.stream()
                .filter(e -> e.parents().stream().noneMatch(all::contains))
                .map(Element::text).map(String::strip).filter(s -> !s.isBlank()).toList();
    }

    /**
     * before 의 항목 중 after 에서 짝을 못 찾은 것.
     *
     * ★ 짝지은 after 항목은 빼고 다음을 찾는다 — 한 항목이 두 항목의 짝 노릇을 하면
     *   "하나가 사라지고 다른 하나가 복제된" 경우를 놓친다.
     */
    private static List<String> lostItems(String before, String after) {
        List<String> pool = new ArrayList<>(itemTexts(after));
        List<String> lost = new ArrayList<>();
        for (String item : itemTexts(before)) {
            String key = core(item);
            int at = -1;
            for (int i = 0; i < pool.size(); i++) {
                if (pool.get(i).contains(key)) { at = i; break; }
            }
            if (at < 0) lost.add(item); else pool.remove(at);
        }
        return lost;
    }

    /** 항목 비교용 핵심 조각 — 말투를 다듬으면 글이 조금 바뀌므로 앞머리만 본다 */
    private static String core(String s) {
        String t = s.strip();
        return t.length() <= 6 ? t : t.substring(0, 6);
    }

    private boolean changed(Pattern p, String before, String after) {
        String a = first(p, before), b = first(p, after);
        return a != null && b != null && !a.equals(b);
    }

    private String first(Pattern p, String html) {
        if (html == null) return null;
        Matcher m = p.matcher(html);
        return m.find() ? m.group() : null;
    }

    private static String targets(List<RawRoute> ops) {
        return ops.stream().map(RawRoute::target).reduce((a, b) -> a + "," + b).orElse("-");
    }

    private static String opsOf(List<RawRoute> ops) {
        return ops.stream().map(RawRoute::op).reduce((a, b) -> a + "," + b).orElse("-");
    }

    private static String describe(List<RawRoute> ops) {
        return opsOf(ops) + " → " + targets(ops);
    }

    // ── 호출 · 비용 ─────────────────────────────────────────────────────────

    private LlmClient.Response chat(LlmClient.Request req) {
        calls++;
        LlmClient.Response res = client.chat(req);
        inTokens += res.inputTokens();
        outTokens += res.outputTokens();
        return res;
    }

    private static LlmClient newClient() {
        return new BedrockClient(env("AWS_REGION", "ap-northeast-2"),
                env("LLM_MODEL", "google.gemma-3-27b-it"), 120);
    }

    private static String env(String k, String fallback) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    // ── 출력 ────────────────────────────────────────────────────────────────

    private void head(String title) {
        report.add("");
        report.add("── " + title + " " + "─".repeat(Math.max(0, 56 - title.length())));
    }

    private void row(boolean ok, String name, List<String> notes) {
        report.add(String.format("%-4s %-22s %s", ok ? "통과" : "실패", shorten(name),
                notes.isEmpty() ? "" : String.join(" · ", notes)));
    }

    private static String shorten(String s) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').strip();
        return t.length() <= 22 ? t : t.substring(0, 21) + "…";
    }

    private void summary(int rPass, int rTotal, int jPass, int jTotal,
            int gPass, int gTotal, int ePass, int eTotal) {
        double cost = (inTokens + outTokens) / 1_000_000.0 * 0.5 * 1400;   // $0.5/1M · 환율 1,400
        report.add("");
        report.add("─".repeat(64));
        // ★ 점수만 남기면 다음에 볼 때 "모델이 바뀐 건지 프롬프트가 바뀐 건지" 를 못 가린다.
        //   둘 다 적어 둔다. 프롬프트는 길어서 해시로 줄인다.
        report.add(String.format("모델            %s / %s", client.providerName(), client.modelName()));
        report.add(String.format("프롬프트 판본    라우터 %s · 생성 %s",
                digest(PromptBuilder.router()), digest(PromptBuilder.generate())));
        report.add(String.format("실행 시각        %s", java.time.LocalDateTime.now()));
        report.add(String.format("라우터 분류      %2d / %2d  (%d%%)   ← 기준선 미달이면 테스트 실패",
                rPass, rTotal, pct(rPass, rTotal)));
        report.add(String.format("판정기 (ASK_BACK) %2d / %2d  (%d%%)", jPass, jTotal, pct(jPass, jTotal)));
        report.add(String.format("생성 1회 성공률   %2d / %2d  (%d%%)", gPass, gTotal, pct(gPass, gTotal)));
        report.add(String.format("수정 1회 성공률   %2d / %2d  (%d%%)", ePass, eTotal, pct(ePass, eTotal)));
        report.add(String.format("토큰            입력 %,d · 출력 %,d", inTokens, outTokens));
        report.add(String.format("이번 실행 비용    약 %,.0f원", cost));
        if (!failureHistogram.isEmpty()) {
            report.add("");
            report.add("실패 유형 (많은 순) — 이게 다음에 고칠 목록이다");
            failureHistogram.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> report.add(String.format("  %-24s %d건", e.getKey(), e.getValue())));
        }
        report.add("─".repeat(64));
    }

    /** 프롬프트가 바뀌었는지만 알면 된다 — 짧게 줄인다 */
    private static String digest(String prompt) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return String.format("%02x%02x%02x%02x", h[0], h[1], h[2], h[3]);
        } catch (java.security.NoSuchAlgorithmException e) {
            return "?";
        }
    }

    private static int pct(int a, int b) { return b == 0 ? 0 : a * 100 / b; }

    private void bump(String code) { failureHistogram.merge(code, 1, Integer::sum); }

    private static String names(List<Failure> fs) {
        return fs.stream().map(f -> f.kind().name()).distinct()
                .reduce((a, b) -> a + "," + b).orElse("");
    }

    /**
     * 화면에도 찍고 파일로도 남긴다.
     *
     * ★ 윈도 콘솔 기본 인코딩(cp949)과 JVM 출력(UTF-8)이 어긋나면 한글이 깨진다.
     *   파일로 남기면 편집기에서 그냥 읽을 수 있다.
     */
    private void print() {
        report.forEach(System.out::println);
        try {
            java.nio.file.Path out = java.nio.file.Path.of("build", "golden-report.txt");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.write(out, report, java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("[골든셋] 결과 파일: " + out.toAbsolutePath());
        } catch (java.io.IOException e) {
            System.out.println("[골든셋] 결과 파일을 못 썼습니다: " + e.getMessage());
        }
    }

    // ── YAML ────────────────────────────────────────────────────────────────

    private static int routerMin(Map<String, Object> doc) {
        Integer v = asInt(doc.get("routerMinPercent"));
        return v == null ? 80 : v;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadCases() throws Exception {
        try (InputStream in = GoldenSetEvalTest.class.getClassLoader()
                .getResourceAsStream("golden/cases.yaml")) {
            if (in == null) throw new IllegalStateException("golden/cases.yaml 이 없습니다");
            return (Map<String, Object>) new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return o == null ? List.of() : (List<Map<String, Object>>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strs(Object o) {
        return o == null ? List.of() : (List<String>) (Collection<?>) o;
    }

    private static String str(Object o) { return o == null ? null : o.toString().trim(); }

    private static Integer asInt(Object o) { return o instanceof Number n ? n.intValue() : null; }

    static { Locale.setDefault(Locale.KOREA); }
}
