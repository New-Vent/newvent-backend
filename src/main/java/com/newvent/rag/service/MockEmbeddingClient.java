package com.newvent.rag.service;

import java.util.Random;

/**
 * 키 없이 도는 가짜 임베딩. CI 기본값이다.
 *
 * ★ 결정적이다. 같은 입력은 같은 벡터를 낸다.
 *   해시 기반이라 단위 테스트에서 검색 순서를 재현할 수 있다.
 */
public class MockEmbeddingClient implements EmbeddingClient {

    private final int dimension;

    public MockEmbeddingClient() {
        this(1024);
    }

    public MockEmbeddingClient(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public String modelName() {
        return "mock";
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
        Random r = new Random(text.hashCode() * 31L + 7);
        float[] v = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            v[i] = r.nextFloat() * 2 - 1;
        }
        return v;
    }
}
