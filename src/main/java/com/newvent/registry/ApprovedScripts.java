package com.newvent.registry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 기본 제공 템플릿 5종이 쓰는 <b>승인된 &lt;script&gt;</b> 목록.
 *
 * ★★ 왜 레지스트리에 있나 — 두 곳이 같은 판정을 해야 한다
 *   PageShell.standalone 은 behavior 가 있는 조각에서 모델이 지어낸 스크립트를 지운다.
 *   그런데 템플릿 5종의 타이머(flash_sale) · 토스트 · 복주머니 뽑기(holiday_gift) 같은
 *   연출은 자체 &lt;script&gt; 로 돈다 — <b>runtime 이 대체하지 않는다.</b>
 *   그 스크립트를 보존해야 한다는 판단은 템플릿 등록({@code SavedTemplateHtml})에서
 *   이미 하고 있었다. 보여 줄 때도 같은 목록을 봐야 한다.
 *
 * ★ 왜 문자열 집합인가 — 바이트 왕복 보장이 목적이다
 *   편집·정화 뒤에 그 스크립트가 <b>글자 하나도 안 틀리고</b> 남았는지 보려는 것이다.
 *   AST 비교가 아니다. 템플릿 리소스가 유일한 승인 원본이고, 거기와 다르면 모델이 지어낸 것이다.
 *
 * ★ 처음 쓸 때 한 번 읽는다 — 리소스가 운영 중에 바뀌지 않는다
 *   {@code ResourceTemplateLoader} 와 같은 규칙으로 {@code templates.json} 과
 *   {@code templates/{code}.html} 을 읽는다. 거기와 중복이지만 레지스트리는
 *   {@code generation.service} 를 몰라야 해서 여기 들고 왔다.
 *
 * ★ static final 초기화로 두지 않는다 — PageShell.noticesHtml 과 같은 이유
 *   거기서 던지면 클래스가 "초기화 실패" 로 기억되어 이후 접근이 전부 NoClassDefFoundError 가 되고,
 *   진짜 원인은 첫 줄에만 남는다. 지연 로딩이면 부를 때마다 같은 원인이 그대로 나온다.
 */
public final class ApprovedScripts {

    private ApprovedScripts() { }

    private static final String DIR = "templates/";
    private static final String INDEX = DIR + "templates.json";

    private static volatile Set<String> scripts;

    /** 승인된 &lt;script&gt; 의 outerHtml 집합 */
    public static Set<String> scripts() {
        Set<String> v = scripts;
        if (v == null) {
            v = load();
            scripts = v;
        }
        return v;
    }

    /** 그 &lt;script&gt; 가 템플릿 승인본인가 */
    public static boolean contains(Element script) {
        return scripts().contains(script.outerHtml());
    }

    private static Set<String> load() {
        try (InputStream in = open(INDEX)) {
            if (in == null) throw new IllegalStateException(
                    "templates/templates.json 을 찾지 못했습니다. 기본 템플릿이 사라지면 승인 목록이 비어 "
                    + "runtime 보조층(ensureParticipate)이 심은 참여 이름표가 들어오는 순간 "
                    + "타이머 · 토스트 같은 템플릿 연출 스크립트가 전부 삭제됩니다.");
            JsonNode arr = new ObjectMapper().readTree(in);

            return java.util.stream.StreamSupport.stream(arr.spliterator(), false)
                    .map(n -> n.get("code").asText())
                    .flatMap(code -> readScripts(code).stream())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new IllegalStateException("승인 스크립트 목록을 읽지 못했습니다", e);
        }
    }

    private static java.util.List<String> readScripts(String code) {
        try (InputStream in = open(DIR + code + ".html")) {
            if (in == null) return java.util.List.of();
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Jsoup.parseBodyFragment(html).select("script").stream()
                    .map(Element::outerHtml)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("템플릿 " + code + " 를 읽지 못했습니다", e);
        }
    }

    /**
     * ★ 클래스로더를 두 군데 본다 — PageShell.loadNotices 와 같은 이유다.
     *   테스트 러너 · devtools 처럼 로더가 갈리는 환경에서 한쪽만 보면 못 찾는다.
     */
    private static InputStream open(String path) {
        InputStream in = ApprovedScripts.class.getResourceAsStream("/" + path);
        if (in != null) return in;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (cl == null) ? null : cl.getResourceAsStream(path);
    }
}
