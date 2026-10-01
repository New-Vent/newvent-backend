package com.newvent.rag.service;

// 평가 (docs/rag-embedding-eval.md, cohere 9/10)에서 정한 값. 바꾸면 청크를 전부 다시 만들어야 함
public class RagConstants {

	private RagConstants() {}
	
	public static final String EMBEDDING_MODEL = "cohere.embed-multilingual-v3";
	public static final String EMBEDDING_REGION = "us-east-1";
	public static final int DIMENSION = 1024;
	
	// 최소 유사도 0.5 -> 유사도 0.5보다 낮으면 검색 결과에서 잘림
	public static final double MIN_SCORE = 0.5;
	// 거리 = 1 - 유사도. findSimilar 에 그대로 넘기는 값
	public static final double MAX_DISTANCE = 1.0 - MIN_SCORE;
	public static final int DEFAULT_TOP_K = 3;
}
