package com.newvent.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.newvent.rag.service.BedrockEmbeddingClient;
import com.newvent.rag.service.EmbeddingClient;
import com.newvent.rag.service.RagConstants;

/**
 * 임베딩 클라이언트를 빈으로 등록. 배포·로컬 모두 Bedrock cohere 를 사용
 * 
 * ★ 만들 때는 AWS 키 확인 X (사용할 때 키 필요). 키가 없어도 앱은 뜸.
 * ★ 테스트는 Spring 없이 new MockEmbeddingClient() 를 직접 만듦
 */
@Configuration
public class RagConfig {

	@Bean
	public EmbeddingClient embeddingClient() {
		return new BedrockEmbeddingClient(
				RagConstants.EMBEDDING_REGION,
				RagConstants.EMBEDDING_MODEL,
				RagConstants.DIMENSION);
	}
}
