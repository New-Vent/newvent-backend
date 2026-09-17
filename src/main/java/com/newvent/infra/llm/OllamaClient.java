package com.newvent.infra.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 로컬 Ollama 호출. 현재 벤치마크 v8.4 의 call() 버전
 *
 * ★ options 를 함부로 바꾸지 말것.
 *   아래 값들은 벤치마크를 돌릴 때 쓴 값과 같다.
 *   바꿔야 한다면 벤치마크도 같이 바꾸고 다시.
 *
 * 자바 21 내장 HttpClient 를 씁니다. 별도 의존성이 없다.
 */
public class OllamaClient implements LlmClient {

    private static final ObjectMapper M = new ObjectMapper();

    // ── 벤치마크 BASE_OPTIONS 와 동일 추후 최적으로 수정──────────────────────────────
    private static final double TEMPERATURE    = 0.2;
    private static final double TOP_P          = 0.9;
    private static final int    TOP_K          = 40;
    private static final double REPEAT_PENALTY = 1.1;
    private static final int    NUM_CTX        = 8192;

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final String keepAlive;
    private final HttpClient http;

    public OllamaClient(String baseUrl, String model, int timeoutSeconds) {
        this(baseUrl, model, timeoutSeconds, "30m");
    }

    /**
     * @param keepAlive 모델을 GPU 에 얼마나 붙잡아 둘지.
     */
    public OllamaClient(String baseUrl, String model, int timeoutSeconds, String keepAlive) {
        this.baseUrl   = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model     = model;
        this.timeout   = Duration.ofSeconds(timeoutSeconds);
        this.keepAlive = keepAlive;
        this.http      = HttpClient.newBuilder()
                                   .connectTimeout(Duration.ofSeconds(5))
                                   .build();
    }

    @Override
    public String providerName() {
        return "ollama:" + model;
    }

    // ══════════════════════════════════════════════════════════════

    @Override
    public Response chat(Request r) {
        String body = buildBody(r);

        long t0 = System.nanoTime();
        HttpResponse<String> res;
        try {
            res = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                               .timeout(timeout)
                               .header("Content-Type", "application/json; charset=utf-8")
                               .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                               .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new LlmCallException(
                    "Ollama 에 연결하지 못했습니다 (" + baseUrl + "). `ollama serve` 가 떠 있나요?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCallException("호출이 중단됐습니다.", e);
        }
        long wallMs = (System.nanoTime() - t0) / 1_000_000;

        if (res.statusCode() != 200) {
            throw new LlmCallException(
                    "Ollama 가 " + res.statusCode() + " 를 돌려줬습니다. "
                    + "모델 이름이 맞나요? (`ollama list` 로 확인) — " + brief(res.body()));
        }

        JsonNode j;
        try {
            j = M.readTree(res.body());
        } catch (JsonProcessingException e) {
            throw new LlmCallException("응답이 JSON 이 아닙니다: " + brief(res.body()), e);
        }

        String content = j.path("message").path("content").asText("");

        // ★ 프롬프트가 캐시에 남아 있으면 Ollama 가 prompt_eval_count 를 아예 안 보냄.
        int inputTokens  = j.path("prompt_eval_count").asInt(0);
        int outputTokens = j.path("eval_count").asInt(0);

        // ★  안 보면 num_predict 에 걸려 잘린 HTML 을 정상으로 받음
        boolean truncated = "length".equals(j.path("done_reason").asText(""));

        return new Response(content, inputTokens, outputTokens, wallMs, truncated);
    }


    private String buildBody(Request r) {
        ObjectNode root = M.createObjectNode();
        root.put("model", model);
        root.put("stream", false);
        root.put("keep_alive", keepAlive);

        ArrayNode messages = root.putArray("messages");
        if (r.system() != null && !r.system().isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", r.system());
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", r.user() == null ? "" : r.user());

        ObjectNode o = root.putObject("options");
        o.put("temperature",    TEMPERATURE);
        o.put("top_p",          TOP_P);
        o.put("top_k",          TOP_K);
        o.put("repeat_penalty", REPEAT_PENALTY);
        o.put("num_ctx",        NUM_CTX);
        o.put("num_predict",    r.mode().maxTokens);   // HTML 1536 · ROUTER 256
        if (r.seed() != null) {
            o.put("seed", r.seed());
        }

        // 라우터는 JSON 한 줄만
        if (r.mode() == Mode.ROUTER) {
            root.put("format", "json");
        }

        try {
            return M.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new LlmCallException("요청 JSON 을 만들지 못했습니다.", e);
        }
    }

    /**
     * 모델을 GPU 에 미리 올려둔다. 빈 메시지를 한 번 보내는 것뿐이다.
     */
    public void preload() {
        try {
            ObjectNode root = M.createObjectNode();
            root.put("model", model);
            root.put("stream", false);
            root.put("keep_alive", keepAlive);
            root.putArray("messages");

            http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                                 .timeout(Duration.ofSeconds(120))
                                 .header("Content-Type", "application/json; charset=utf-8")
                                 .POST(HttpRequest.BodyPublishers.ofString(
                                         M.writeValueAsString(root), StandardCharsets.UTF_8))
                                 .build(),
                      HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // 예열 실패는 서비스를 막지 않는다. 첫 호출이 조금 느릴 뿐이다.
        }
    }

    private static String brief(String s) {
        if (s == null) return "(없음)";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
