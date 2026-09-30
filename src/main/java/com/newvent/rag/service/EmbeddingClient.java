package com.newvent.rag.service;

/**
 * 텍스트 → 벡터 변환 출입구. LlmClient 와 같은 자리다.
 *
 * ★ dimension() 이 실제 반환 길이를 보장한다.
 *   호출자는 길이를 믿고 pgvector 컬럼에 넣는다.
 */
public interface EmbeddingClient {

    /** 텍스트 1건을 벡터로 바꾼다. 빈 입력은 거부한다 */
    float[] embed(String text);

    /** 벡터 길이. vector(n) 컬럼과 같아야 한다 */
    int dimension();

    /** 어떤 모델인지 — 로그·rag_chunks.embedding_model 기록용 */
    String modelName();
}
