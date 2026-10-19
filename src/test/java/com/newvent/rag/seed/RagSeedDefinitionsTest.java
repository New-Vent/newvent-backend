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

    // V19 ck_rag_chunks_block_key 와 같은 값이어야 한다. 어긋나면 적재가 터진다.
    private static final Set<String> ALLOWED_BLOCKS = Set.of(
            "hero", "highlight", "intro", "stats", "benefits",
            "prize", "coupon", "compare", "audience", "steps",
            "schedule", "faq", "notices", "cta");

    private final RagChunkingService chunking = new RagChunkingService();

    @Test
    @DisplayName("시드는 40건이다 (스포츠 7 + 시즌 9 + 멤버십 10 + 특가 8 + 출시 6)")
    void count() {
        assertEquals(7, RagSeedDataGenerator.sports().size());
        assertEquals(9, RagSeedDataGenerator.season().size());
        assertEquals(10, RagSeedDataGenerator.membership().size());
        assertEquals(8, RagSeedDataGenerator.sale().size());
        assertEquals(6, RagSeedDataGenerator.launch().size());
        assertEquals(40, RagSeedDataGenerator.definitions().size());
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
    @DisplayName("장기가입 안내(intro)는 800자를 넘어 2청크 이상으로 나뉜다")
    void longIntroSplits() {
        SeedDefinition def = RagSeedDataGenerator.definitions().stream()
                .filter(d -> d.title().equals("장기가입 감사 프로그램")).findFirst().orElseThrow();
        assertTrue(chunking.chunk(def.html()).size() >= 2);
    }

    @Test
    @DisplayName("친구 초대 FAQ는 질문 3개 이상을 담는다 (중첩 구조 검증)")
    void nestedFaqPresent() {
        SeedDefinition def = RagSeedDataGenerator.definitions().stream()
                .filter(d -> d.title().equals("친구 초대 리워드")).findFirst().orElseThrow();
        long dtCount = def.html().split("<dt>", -1).length - 1;
        assertTrue(dtCount >= 3, "dt 개수=" + dtCount);
    }

    @Test
    @DisplayName("카테고리별 테마 클래스가 들어 있다 (5종 구분)")
    void themePerCategory() {
        for (SeedDefinition def : RagSeedDataGenerator.sports()) {
            assertTrue(def.html().contains("theme-sports"), def.title());
        }
        for (SeedDefinition def : RagSeedDataGenerator.season()) {
            assertTrue(def.html().contains("theme-holiday"), def.title());
        }
        for (SeedDefinition def : RagSeedDataGenerator.membership()) {
            assertTrue(def.html().contains("theme-vip"), def.title());
        }
        for (SeedDefinition def : RagSeedDataGenerator.sale()) {
            assertTrue(def.html().contains("theme-sale"), def.title());
        }
        for (SeedDefinition def : RagSeedDataGenerator.launch()) {
            assertTrue(def.html().contains("theme-launch"), def.title());
        }
    }

    @Test
    @DisplayName("주제 밖 5종이 없다 (영어·메타문장·기호나열·최소구성·중복비교표)")
    void offTopicAbsent() {
        Set<String> titles = new HashSet<>();
        for (SeedDefinition d : RagSeedDataGenerator.definitions()) {
            titles.add(d.title());
        }
        assertTrue(titles.stream().noneMatch(t -> t.contains("Welcome Global")
                || t.contains("초긴 안내문") || t.contains("특수문자 대잔치")
                || t.contains("최소 구성") || t.contains("요금제 비교표")),
                "주제 밖 시드 잔재: " + titles);
    }
}
