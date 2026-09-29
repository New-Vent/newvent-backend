package com.newvent.rag.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Ollama 임베딩 호출 (/api/embed). bge-m3·mxbai·arctic 공용, 모델명만 다르다.
 *
 * ★ 자바 21 내장 HttpClient 를 쓴다. OllamaClient 와 같은 방식이다. 별도 의존성 없다.
 */
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final ObjectMapper M = new ObjectMapper();

    private final String baseUrl;
    private final String model;
    private final int dimension;
    private final Duration timeout;
    private final HttpClient http;

    public OllamaEmbeddingClient(String baseUrl, String model) {
        this(baseUrl, model, 1024);
    }

    public OllamaEmbeddingClient(String baseUrl, String model, int dimension) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.dimension = dimension;
        this.timeout = Duration.ofSeconds(60);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("임베딩 입력이 비어 있습니다.");
        }

        String body;
        try {
            ObjectNode root = M.createObjectNode();
            root.put("model", model);
            root.put("input", text);
            body = M.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("요청 JSON 을 만들지 못했습니다.", e);
        }

        HttpResponse<String> res;
        try {
            res = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/embed"))
                            .timeout(timeout)
                            .header("Content-Type", "application/json; charset=utf-8")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Ollama 에 연결하지 못했습니다 (" + baseUrl + "). `ollama serve` 가 떠 있나요?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("호출이 중단됐습니다.", e);
        }

        if (res.statusCode() != 200) {
            throw new IllegalStateException(
                    "Ollama 가 " + res.statusCode() + " 를 돌려줬습니다. 모델을 받았나요? (`ollama pull " + model + "`)");
        }

        try {
            JsonNode root = M.readTree(res.body());
            ArrayNode all = (ArrayNode) root.path("embeddings");
            ArrayNode arr = (ArrayNode) all.get(0);
            float[] v = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                v[i] = (float) arr.get(i).asDouble();
            }
            if (v.length != dimension) {
                throw new IllegalStateException(
                        "차원 불일치: 기대 " + dimension + ", 실제 " + v.length);
            }
            return v;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("임베딩 응답을 읽지 못했습니다 (" + model + ").", e);
        }
    }
}
