package com.newvent.rag.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.rag.seed.RagSeedDataGenerator.SeedDefinition;
import com.newvent.rag.service.RagChunkingService;
import com.newvent.rag.service.RagChunkingService.Chunk;

/** 시드 정의 검증. DB 없이 돈다 — 실제 RagChunkingService 로 청킹까지 확인한다. */
class RagSeedDefinitionsTest {

    // V14 ck_rag_chunks_block_key 와 같은 값이어야 한다. 어긋나면 적재가 터진다.
    private static final Set<String> ALLOWED_BLOCKS = Set.of(
            "hero", "highlight", "intro", "benefits", "compare",
            "audience", "steps", "faq", "notices", "cta");

    private final RagChunkingService chunking = new RagChunkingService();

    @Test
    @DisplayName("시드는 29건이다 (템플릿 변형 14 + 실제형 10 + 엣지 5)")
    void count() {
        assertEquals(14, RagSeedDataGenerator.templateVariants().size());
        assertEquals(10, RagSeedDataGenerator.realistic().size());
        assertEquals(5, RagSeedDataGenerator.edgeCases().size());
        assertEquals(29, RagSeedDataGenerator.definitions().size());
    }

    @Test
    @DisplayName("제목이 서로 다르다 — 멱등 가드와 눈으로 구분하기 위해")
    void titlesUnique() {
        List<String> titles = RagSeedDataGenerator.definitions().stream()
                .map(SeedDefinition::title).toList();
        assertEquals(titles.size(), new HashSet<>(titles).size());
    }

    @Test
    @DisplayName("전건 1청크 이상 나오고 blockKey 가 CHECK 안에 든다")
    void allChunkable() {
        for (SeedDefinition def : RagSeedDataGenerator.definitions()) {
            List<Chunk> chunks = chunking.chunk(def.html());
            assertTrue(!chunks.isEmpty(), def.title() + ": 청크 0개");
            for (Chunk c : chunks) {
                assertTrue(ALLOWED_BLOCKS.contains(c.blockKey()),
                        def.title() + ": CHECK 위반 blockKey=" + c.blockKey());
            }
        }
    }

    @Test
    @DisplayName("초긴 안내문은 2청크 이상으로 나뉜다")
    void longHeroSplits() {
        SeedDefinition def = RagSeedDataGenerator.definitions().stream()
                .filter(d -> d.title().equals("초긴 안내문 이벤트")).findFirst().orElseThrow();
        assertTrue(chunking.chunk(def.html()).size() >= 2);
    }

    @Test
    @DisplayName("최소 구성은 hero + cta 2청크만 나온다")
    void minimalTwoChunks() {
        SeedDefinition def = RagSeedDataGenerator.definitions().stream()
                .filter(d -> d.title().equals("최소 구성 이벤트")).findFirst().orElseThrow();
        List<Chunk> chunks = chunking.chunk(def.html());
        assertEquals(2, chunks.size());
        assertEquals(Set.of("hero", "cta"),
                Set.of(chunks.get(0).blockKey(), chunks.get(1).blockKey()));
    }

    @Test
    @DisplayName("엣지 케이스 5종 제목이 빠짐없이 있다")
    void edgeTitles() {
        Set<String> titles = new HashSet<>();
        for (SeedDefinition d : RagSeedDataGenerator.definitions()) {
            titles.add(d.title());
        }
        assertTrue(titles.containsAll(Set.of(
                "초긴 안내문 이벤트", "특수문자 대잔치", "최소 구성 이벤트",
                "Welcome Global Event", "요금제 비교표 이벤트")));
    }
}
