package com.newvent.rag.service;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import com.newvent.registry.Block;

/**
 * EventVersion.htmlContent -> 임베딩용 텍스트 청크
 * DB를 모르는 순수 로직 코드이기 때문에 단위 테스트는 DB 없이 진행
 */
@Service
public class RagChunkingService {

	// 한 청크 최대 글자 수. 블록 하나가 통째로 들어가게 800으로 설정
	public static final int MAX_CHUNK_CHARS = 800;
	// 앞 뒤 청크가 겹치는 글자 수. 경계에서 문맥이 끊기지 않도록
	public static final int OVERLAP_CHARS = 100;

	public record Chunk(String blockKey, int chunkIndex, String content) {}

	public List<Chunk> chunk(String html){
		if(html == null || html.isBlank()) {
			return List.of();
		}
		Document doc = Jsoup.parseBodyFragment(html);
		List<Chunk> out = new ArrayList<>();
		// ★ 모델이 만드는 블록만 돈다. notices는 모든 이벤트에 같은 문구라 검색 결과만 오염시킨다.
		// 	 블록 키는 Block enum 에서만 가져옴 (rag_chunks CHECK 값과 같아야 함)
		for (Block block : Block.llmBlocks()) {
			Element el = doc.body().selectFirst(block.selector());
			if(el == null) {
				continue;
			}
			String text = el.text().strip();
			if(text.isBlank()) {
				continue;
			}
			int step = MAX_CHUNK_CHARS - OVERLAP_CHARS;
			int idx = 0;
			for(int start = 0; start < text.length(); start += step, idx++) {
				int end = Math.min(start + MAX_CHUNK_CHARS, text.length());
				out.add(new Chunk(block.key(), idx, text.substring(start, end)));
				if (end == text.length()) {
					break;
				}
			}
		}
		return out;
	}
}
