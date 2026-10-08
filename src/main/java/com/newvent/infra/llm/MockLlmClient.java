package com.newvent.infra.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 키도 Ollama 도 Bedrock 도 없이 도는 가짜 클라이언트. <b>기본값이다.</b>
 *
 * ★ 이건 테스트 도구가 아니라 <b>데모·연동 도구</b>다
 *   단위 테스트는 {@code FakeRetry} · {@code FakeRouter} 로 모델을 아예 안 부른다.
 *   이 클래스가 쓰이는 자리는 "서버를 띄워 프론트와 붙여 보는" 때다.
 *   그래서 "아무거나 돌려주면 된다" 가 아니라 <b>진짜처럼 굴어야 한다.</b>
 *
 * ★ 지켜야 하는 것 둘
 *   ① 라우터는 <b>요청문을 보고</b> 영역을 고른다.
 *      고정 응답이면 "제목 바꿔줘" 를 쳐도 혜택이 바뀌어서 데모가 고장으로 보인다.
 *   ② 수정 출력은 <b>받은 원본을 그대로 쓰고 문구만 바꾼다.</b>
 *      고정 HTML 을 돌려주면 템플릿의 id·class·data-slot 이 사라져서
 *      checkPreserved 가 막는다. 막는 게 맞는 동작이라 더 헷갈린다.
 *      (실제로 template_2 의 id="pouchSection" 이 여기 걸렸다)
 *
 * ★ 실제 모델을 흉내 내지 않는다
 *   문장을 다듬지 못하므로 "수정됨 · " 을 앞에 붙인다. 무엇이 바뀌었는지
 *   화면에서 바로 보이는 게 데모에서는 더 쓸모 있다.
 *
 * 데모용 조작
 *   요청문에 FAIL 이 들어가면   → 검증 실패 경로 (empty_benefits)
 *   아는 영역 이름이 없으면      → 빈 ops → 되묻기(ASK_BACK)
 *   '작은따옴표' 로 감싼 문구     → 라우터 content 로 실린다
 *   색·컬러 를 말하면           → 인라인 style 을 붙인다
 */
public class MockLlmClient implements LlmClient {

    // ── 영역 이름 사전 ────────────────────────────────────────────
    //   순서가 곧 우선순위다. "참여 방법" 이 "참여" 보다 먼저 와야 한다

    private static final Map<String, String[]> KEYWORDS = new LinkedHashMap<>();
    static {
        KEYWORDS.put("steps",    new String[]{"참여방법", "참여 방법", "진행방법", "진행 방법",
                                              "참여절차", "단계", "스텝"});
        KEYWORDS.put("notices",  new String[]{"유의사항", "주의사항", "유의 사항", "안내사항"});
        KEYWORDS.put("benefits", new String[]{"혜택", "경품", "선물", "사은품", "리워드"});
        KEYWORDS.put("hero",     new String[]{"제목", "타이틀", "헤드", "소개문", "소개 문구", "헤더"});
        KEYWORDS.put("cta",      new String[]{"버튼", "참여하기", "cta", "신청 버튼"});
    }

    private static final String[] DELETE_WORDS = {"지워", "삭제", "없애", "빼줘", "빼 줘", "제거"};
    private static final String[] ADD_WORDS    = {"추가", "넣어", "새로 만들", "붙여"};
    private static final String[] STYLE_WORDS  = {"색", "컬러", "굵게", "크게", "작게", "스타일", "디자인"};

    /** '작은따옴표' · "큰따옴표" · “곡선따옴표” 안의 문구 */
    private static final Pattern QUOTED =
            Pattern.compile("['\"‘“]([^'\"’”]{1,60})['\"’”]");

    /** PromptBuilder.edit() 이 만든 시스템 프롬프트에서 대상 영역을 뽑는다 */
    private static final Pattern EDIT_TARGET =
            Pattern.compile("data-block=\"([a-z]+)\"");

    /** EditService.userPrompt() 가 붙이는 표지 */
    private static final String CURRENT_MARK = "[현재 내용";

    // ── 고정 HTML ─────────────────────────────────────────────────

    /** 백지 생성 결과 */
    private static final String OK = """
            <section data-block="hero">
              <h1>여름 데이터 대방출</h1>
              <p>이번 여름 데이터 걱정 없이 마음껏 즐기세요</p>
            </section>
            <section data-block="benefits">
              <ul>
                <li>데이터 3GB 즉시 지급</li>
                <li>월 요금 30% 할인</li>
                <li>제휴 카페 음료 쿠폰</li>
              </ul>
            </section>
            <section data-block="steps">
              <ol>
                <li>이벤트 페이지에서 요금제 선택</li>
                <li>온라인으로 가입 신청</li>
              </ol>
            </section>
            <section data-block="cta">
              <a href="#" class="btn">참여하기</a>
            </section>""";

    /** benefits 안이 맨 텍스트 — empty_benefits 로 걸린다 */
    private static final String BROKEN = """
            <section data-block="hero">
              <h1>여름 데이터 대방출</h1>
            </section>
            <section data-block="benefits">
              데이터 3GB 지급, 요금 할인
            </section>
            <section data-block="cta">
              <a href="#" class="btn">참여하기</a>
            </section>""";

    /** 없던 영역을 새로 만들 때(ADD). 원본이 없으니 지어내는 수밖에 없다 */
    private static final Map<String, String> FRESH = Map.of(
            "hero", """
                    <section data-block="hero"><h1>새 이벤트</h1>\
                    <p>지금 참여하고 혜택을 받아가세요</p></section>""",
            "benefits", """
                    <section data-block="benefits"><ul>\
                    <li>데이터 3GB 즉시 지급</li><li>월 요금 30% 할인</li></ul></section>""",
            "steps", """
                    <section data-block="steps"><ol>\
                    <li>이벤트 페이지에서 요금제 선택</li><li>온라인으로 가입 신청</li></ol></section>""",
            "cta", """
                    <section data-block="cta"><a href="#" class="btn">참여하기</a></section>""");

    private final long delayMs;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public MockLlmClient()             { this(400); }
    public MockLlmClient(long delayMs) { this.delayMs = delayMs; }

    // ── 진입 ──────────────────────────────────────────────────────

    @Override
    public Response chat(Request r) {
        sleep();                                   // 로딩 UI 를 볼 수 있게 약간 지연

        String body = (r.mode() == Mode.ROUTER)
                ? route(r.user())
                : html(r);

        return new Response(
                body,
                estimateTokens(r.system()) + estimateTokens(r.user()),
                estimateTokens(body),
                delayMs,
                false);
    }

    @Override
    public String providerName() { return "mock"; }

    @Override
    public String modelName() { return "mock"; }

    // ── 라우터 ────────────────────────────────────────────────────

    /**
     * 요청문에서 영역과 동작을 고른다.
     *
     * ★ 빈 ops 를 돌려주는 경우가 있다
     *   아는 영역 이름이 하나도 없을 때다. RouteParser 가 빈 배열을 실패로 보므로
     *   EditService 가 되묻기로 끝낸다. <b>그게 데모에서 보여주고 싶은 경로다.</b>
     */
    private static String route(String user) {
        String text = (user == null) ? "" : user.toLowerCase();

        // 등장 위치 순으로 영역을 모은다 — 관리자가 말한 순서대로 실행된다
        List<int[]> found = new ArrayList<>();          // {위치, 사전 인덱스}
        List<String> keys = new ArrayList<>(KEYWORDS.keySet());
        for (int i = 0; i < keys.size(); i++) {
            int at = firstIndexOf(text, KEYWORDS.get(keys.get(i)));
            if (at >= 0) found.add(new int[]{at, i});
        }
        found.sort((a, b) -> Integer.compare(a[0], b[0]));

        StringJoiner ops = new StringJoiner(",", "{\"ops\":[", "]}");
        String quoted = firstQuoted(user);

        int n = 0;
        for (int[] hit : found) {
            if (n++ >= 3) break;                        // RouteParser.MAX_OPS
            String key = keys.get(hit[1]);
            String op = opFor(text, hit[0]);

            // 따옴표 안 문구는 첫 연산에만 싣는다. 둘 다에 실으면 지어내는 꼴이다
            String content = (n == 1) ? quoted : null;

            ops.add("{\"op\":\"" + op + "\",\"target\":\"" + key + "\",\"content\":"
                    + (content == null ? "null" : "\"" + escape(content) + "\"") + "}");
        }
        return ops.toString();                          // 하나도 없으면 {"ops":[]}
    }

    /**
     * 동작을 고른다. <b>영역 이름 근처의 말을 본다.</b>
     *
     * ★ 문장 전체를 보면 "혜택 추가하고 참여방법 지워줘" 에서 둘 다 ADD 가 된다.
     *   영역 이름 뒤쪽 30자만 보면 대체로 맞는다.
     */
    private static String opFor(String text, int at) {
        String near = text.substring(at, Math.min(text.length(), at + 30));
        if (containsAny(near, DELETE_WORDS)) return "DELETE";
        if (containsAny(near, ADD_WORDS))    return "ADD";
        if (containsAny(near, STYLE_WORDS))  return "STYLE";
        return "EDIT";
    }

    // ── HTML ──────────────────────────────────────────────────────

    private static String html(Request r) {
        String system = (r.system() == null) ? "" : r.system();
        String user   = (r.user()   == null) ? "" : r.user();

        // 수정 프롬프트는 changeSummary를 함께 요청한다.
        if (system.contains("changeSummary")) {

            // 수정 결과를 JSON으로 반환
            String editedHtml = user.contains("FAIL")
                ? BROKEN
                : editBlock(system, user);

            String summary = targetKey(system) + " 영역 내용 수정";

            return editResponse(editedHtml, summary);
        }

        // 검증 실패 경로를 손으로 보고 싶을 때
        if (user.contains("FAIL")) return BROKEN;

        return OK;
    }

    private static String editResponse(String html, String changeSummary) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                "html", html,
                "changeSummary", changeSummary));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Mock 수정 응답 직렬화 실패", e);
        }
    }

    /**
     * 받은 원본을 그대로 쓰고 문구만 바꾼다.
     *
     * ★ 여기가 이 클래스의 핵심이다
     *   고정 HTML 을 돌려주면 템플릿의 id·class·data-slot 이 사라진다.
     *   checkPreserved 가 그걸 막으므로 4회 재시도 끝에 실패한다.
     *   막는 게 맞는 동작이라 데모에서 보면 서버가 고장난 것처럼 보인다.
     */
    private static String editBlock(String system, String user) {
        String current = currentBlock(user);
        if (current == null) {
            // ADD — 그 영역이 아직 없다. 지어내는 수밖에 없다
            String key = targetKey(system);
            return FRESH.getOrDefault(key, FRESH.get("benefits"));
        }

        Document doc = Jsoup.parseBodyFragment(current);
        doc.outputSettings().prettyPrint(false);
        Element block = doc.body().selectFirst("[data-block]");
        if (block == null) return current;

        // ★ Element.text() 를 쓰면 안 된다 — 자식 요소를 통째로 지운다.
        //   data-slot 이 붙은 <span> 이 거기 있을 수 있다
        List<TextNode> texts = new ArrayList<>();
        NodeTraversor.traverse((node, depth) -> {
            if (node instanceof TextNode t && !t.isBlank()) texts.add(t);
        }, block);

        if (!texts.isEmpty()) {
            TextNode first = texts.get(0);
            String before = first.text();
            // 앞뒤 공백을 지키고 가운데만 간다 — 안 그러면 옆 태그와 붙는다
            String lead  = before.startsWith(" ") ? " " : "";
            String trail = before.endsWith(" ")   ? " " : "";
            first.text(lead + "수정됨 · " + before.strip() + trail);
        }

        // "색" 얘기가 있으면 인라인 style 을 붙인다.
        // ★ <style> 태그가 아니다 — 세이프리스트가 태그는 지우고 속성만 남긴다
        if (containsAny(user.toLowerCase(), STYLE_WORDS)) {
            Element target = block.selectFirst("a, button, h1");
            if (target != null) target.attr("style", "color:#d60076;font-weight:700");
        }

        return block.outerHtml();
    }

    /** EditService.userPrompt() 가 붙인 "[현재 내용 …]" 아래의 HTML */
    private static String currentBlock(String user) {
        int mark = user.indexOf(CURRENT_MARK);
        if (mark < 0) return null;
        int open = user.indexOf('<', mark);
        if (open < 0) return null;

        Document doc = Jsoup.parseBodyFragment(user.substring(open));
        doc.outputSettings().prettyPrint(false);

        Element block = doc.body().selectFirst("section[data-block]");
        return block == null ? null : block.outerHtml();
    }

    private static String targetKey(String system) {
        Matcher m = EDIT_TARGET.matcher(system);
        return m.find() ? m.group(1) : "benefits";
    }

    // ── 잡동사니 ──────────────────────────────────────────────────

    private static int firstIndexOf(String text, String[] words) {
        int best = -1;
        for (String w : words) {
            int at = text.indexOf(w.toLowerCase());
            if (at >= 0 && (best < 0 || at < best)) best = at;
        }
        return best;
    }

    private static boolean containsAny(String text, String[] words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    private static String firstQuoted(String user) {
        if (user == null) return null;
        Matcher m = QUOTED.matcher(user);
        return m.find() ? m.group(1).strip() : null;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    /** 한글은 대략 1.5자당 1토큰. 로그 모양을 보려는 용도지 정확한 값이 아니다 */
    private static int estimateTokens(String s) {
        return s == null ? 0 : (int) (s.length() / 1.5);
    }

    private void sleep() {
        if (delayMs <= 0) return;
        try { Thread.sleep(delayMs); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
