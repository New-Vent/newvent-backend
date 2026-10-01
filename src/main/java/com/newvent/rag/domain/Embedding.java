package com.newvent.rag.domain;

/**
 * 임베딩 저장용 자리표시. 1차는 RagChunk 가 rag_chunks를 맡는다.
 * ★ @Entity를 붙이지 않음. 테이블 없는 엔티티는 ddl-auto=validate 에서 기동 실패
 */
public class Embedding {

	private Embedding() {}
}
