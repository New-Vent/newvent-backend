package com.newvent.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.rag.service.RagChunkingService.Chunk;

class RagChunkingServiceTest {

    private final RagChunkingService chunking = new RagChunkingService();

    private static final String HTML = """
            <div class="ev-container event-page">
              <section data-block="hero"><h1>봄맞이 이벤트</h1><p>3월 한 달간 진행합니다</p></section>
              <section data-block="benefits"><ul><li>신규 가입 10% 쿠폰</li><li>추천인 5000포인트</li></ul></section>
              <section data-block="steps"><ol><li>가입한다</li><li>참여 버튼을 누른다</li></ol></section>
              <section data-block="notices"><p>유의사항 문구</p></section>
              <section data-block="cta"><button>참여하기</button></section>
            </div>
            """;

    @Test
    @DisplayName("모델이 만드는 4개 블록만 청크가 된다 (notices 제외)")
    void 네_블록만() {
        List<Chunk> chunks = chunking.chunk(HTML);
        assertEquals(List.of("hero", "benefits", "steps", "cta"),
                chunks.stream().map(Chunk::blockKey).toList());
    }

    @Test
    @DisplayName("2000자 블록은 800자씩 100자 겹쳐 3청크가 된다")
    void 긴_블록은_나뉜다() {
        String html = "<section data-block=\"benefits\"><p>" + "가".repeat(2000) + "</p></section>";
        List<Chunk> chunks = chunking.chunk(html);
        assertEquals(3, chunks.size());
        assertEquals(List.of(0, 1, 2), chunks.stream().map(Chunk::chunkIndex).toList());
        assertEquals(800, chunks.get(0).content().length());
        assertEquals(chunks.get(0).content().substring(700),
                chunks.get(1).content().substring(0, 100));
    }

    @Test
    @DisplayName("빈 입력·없는 블록은 빈 리스트")
    void 빈_입력() {
        assertTrue(chunking.chunk(null).isEmpty());
        assertTrue(chunking.chunk("   ").isEmpty());
        assertTrue(chunking.chunk("<div>블록 없음</div>").isEmpty());
    }
}
