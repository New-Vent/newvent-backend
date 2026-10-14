package com.newvent.rag.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * Bedrock 임베딩 호출. Titan·Cohere 둘 다 InvokeModel 이다 (Converse 아님).
 *
 * ★ 모델마다 요청·응답 모양이 다르다. 분기는 여기서 끝낸다.
 *   Titan:  {"inputText": "..."} → {"embedding": [...]}
 *   Cohere: {"texts": [...], "input_type": "..."} → {"embeddings": {"float": [...]}}
 *
 * ★ 자격증명은 코드에서 안 만진다. EC2 는 IAM 역할, 로컬은 환경변수로 SDK 가 알아서 가져간다.
 */
public class BedrockEmbeddingClient implements EmbeddingClient {

    private static final ObjectMapper M = new ObjectMapper();

    private final BedrockRuntimeClient bedrock;
    private final String modelId;
    private final int dimension;
    private final boolean titan;

    public BedrockEmbeddingClient(String region, String modelId) {
        this(region, modelId, 1024);
    }

    public BedrockEmbeddingClient(String region, String modelId, int dimension) {
        // ★ 재시도 3회 + 지터 백오프. 야간 전체 재색인처럼 수백 건 순차 호출하면 스로틀이 필연이라,
        //   없으면 터진 이벤트는 다음날까지 검색 공백이 생긴다. API 타임아웃 60초 (임베딩 1건 기준 넉넉).
        this.bedrock = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(c -> c
                        .retryPolicy(RetryPolicy.builder()
                                .numRetries(3)
                                .backoffStrategy(EqualJitterBackoffStrategy.builder()
                                        .baseDelay(Duration.ofMillis(200))
                                        .maxBackoffTime(Duration.ofSeconds(5))
                                        .build())
                                .throttlingBackoffStrategy(EqualJitterBackoffStrategy.builder()
                                        .baseDelay(Duration.ofMillis(500))
                                        .maxBackoffTime(Duration.ofSeconds(10))
                                        .build())
                                .build())
                        .apiCallTimeout(Duration.ofSeconds(60)))
                .build();
        this.modelId = modelId;
        this.dimension = dimension;
        this.titan = modelId.contains("titan");
    }

    @Override
    public String modelName() {
        return modelId;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[] embed(String text) {
        return embedWithInputType(text, "search_query");
    }

    /** 문서 색인용. 질의용 벡터와 다른 공간에 두어 매칭 정밀도를 올린다. */
    @Override
    public float[] embedDocument(String text) {
        return embedWithInputType(text, "search_document");
    }

    private float[] embedWithInputType(String text, String inputType) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("임베딩 입력이 비어 있습니다.");
        }

        String body = titan ? titanBody(text) : cohereBody(text, inputType);
        String out;
        try {
            InvokeModelResponse res = bedrock.invokeModel(InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromString(body, StandardCharsets.UTF_8))
                    .build());
            out = res.body().asUtf8String();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Bedrock 임베딩 호출 실패 (" + modelId + "). 자격증명·리전·모델 승인을 보세요.", e);
        }

        try {
            JsonNode root = M.readTree(out);
            ArrayNode arr = titan
                    ? (ArrayNode) root.path("embedding")
                    : (ArrayNode) root.path("embeddings").path("float").get(0);
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
            throw new IllegalStateException("임베딩 응답을 읽지 못했습니다 (" + modelId + ").", e);
        }
    }

    private String titanBody(String text) {
        try {
            ObjectNode root = M.createObjectNode();
            root.put("inputText", text);
            if (dimension != 1024) {
                root.put("dimensions", dimension);
            }
            return M.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("요청 JSON 을 만들지 못했습니다.", e);
        }
    }

    private String cohereBody(String text, String inputType) {
        try {
            ObjectNode root = M.createObjectNode();
            root.putArray("texts").add(text);
            root.put("input_type", inputType);
            root.putArray("embedding_types").add("float");
            return M.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("요청 JSON 을 만들지 못했습니다.", e);
        }
    }
}
