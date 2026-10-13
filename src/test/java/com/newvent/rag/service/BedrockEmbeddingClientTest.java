package com.newvent.rag.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 클라이언트 생성 검증. 네트워크·키 없이 돈다 (키는 호출 때 확인). */
class BedrockEmbeddingClientTest {

    @Test
    @DisplayName("재시도 정책 포함 생성이 터지지 않는다")
    void buildsWithRetryPolicy() {
        BedrockEmbeddingClient cohere = assertDoesNotThrow(
                () -> new BedrockEmbeddingClient("us-east-1", "cohere.embed-multilingual-v3"));
        assertEquals("cohere.embed-multilingual-v3", cohere.modelName());
        assertEquals(1024, cohere.dimension());

        BedrockEmbeddingClient titan = assertDoesNotThrow(
                () -> new BedrockEmbeddingClient("us-east-1", "amazon.titan-embed-text-v2:0"));
        assertEquals("amazon.titan-embed-text-v2:0", titan.modelName());
    }
}
