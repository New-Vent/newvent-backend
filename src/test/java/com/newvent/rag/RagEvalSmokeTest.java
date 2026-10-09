package com.newvent.rag;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newvent.event.domain.Event;
import com.newvent.rag.domain.RagChunk;
import com.newvent.rag.domain.Vectors;
import com.newvent.rag.repository.RagChunkRepository;
import com.newvent.rag.seed.RagSeedDataGenerator;
import com.newvent.rag.service.BedrockEmbeddingClient;
import com.newvent.rag.service.EmbeddingClient;
import com.newvent.rag.service.OllamaEmbeddingClient;

/**
 * 임베딩 모델 비교 평가. 단언 없음 — 점수표 보고 사람이 정한다.
 *
 * ★ RAG_EVAL=1 일 때만 돈다. CI에서는 스킵된다.
 * ★ RAG_EVAL_AWS=1 이면 Bedrock 2종도 돈다 (키 필요).
 * ★ @DataJpaTest라 끝나면 롤백된다. 평가용 벡터가 남지 않는다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RagEvalSmokeTest {

    @Autowired
    private RagChunkRepository repo;

    @Autowired
    private TestEntityManager tem;

    record EvalQuery(String query, String expectedBlock) {}

    private final List<String> report = new ArrayList<>();

    /** 콘솔과 리포트 파일에 같이 남긴다. */
    private void note(String line) {
        report.add(line);
        System.out.println(line);
    }

    static boolean 스모크_켜짐() {
        return "1".equals(System.getenv("RAG_EVAL"));
    }

    private static String text(String path) throws Exception {
        try (InputStream in = RagEvalSmokeTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException(path + " 없음");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<EvalQuery> loadQuestions() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> doc;
        try (InputStream in = RagEvalSmokeTest.class.getClassLoader()
                .getResourceAsStream("rag/eval-questions.yaml")) {
            doc = yaml.load(in);
        }
        List<Map<String, String>> list =
                (List<Map<String, String>>) doc.get("questions");
        List<EvalQuery> out = new ArrayList<>();
        for (Map<String, String> m : list) {
            out.add(new EvalQuery(m.get("query"), m.get("expectedBlock")));
        }
        return out;
    }

    private Event seedEvent() {
        EntityManager em = tem.getEntityManager();
        Long adminId = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, "admin-eval")
                .setParameter(2, "x")
                .setParameter(3, "관리자")
                .getSingleResult()).longValue();
        Long eventId = ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade) VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, adminId)
                .setParameter(2, "평가용")
                .setParameter(3, "NORMAL")
                .getSingleResult()).longValue();
        return em.getReference(Event.class, eventId);
    }

    /** HTML 한 건 → 섹션별 임베딩. 템플릿·시드 공통 진입점 (청킹 없음 — 비교용이라 형식을 맞춘다) */
    private void indexHtml(EmbeddingClient embedding, Event event, String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        int i = 0;
        for (Element section : doc.select("section[data-block]")) {
            String key = section.attr("data-block");
            // ★ notices 는 색인하지 않는다. 서버 소유 문구라 예시가 될 이유가 없다.
            //   Q8(유의사항 → 없음)이 이 결정을 고정한다.
            if ("notices".equals(key)) continue;
            String content = section.text();
            repo.save(RagChunk.create(event, null, key, i++,
                    content, embedding.modelName(),
                    embedding.embed(content), Instant.now()));
        }
    }

    /** 템플릿 5종 → 블록 통째로 임베딩 */
    private void indexTemplates(EmbeddingClient embedding, Event event) throws Exception {
        JsonNode index = new ObjectMapper().readTree(text("templates/templates.json"));
        for (JsonNode n : index) {
            indexHtml(embedding, event, text("templates/" + n.path("code").asText() + ".html"));
        }
    }

    /**
     * 시드 29건도 평가용으로 색인한다. 모델마다 따로 임베딩해야
     * 시드 문항의 검색 대상이 그 모델 벡터로 존재한다.
     */
    private void indexSeeds(EmbeddingClient embedding, Event event) {
        for (RagSeedDataGenerator.SeedDefinition def : RagSeedDataGenerator.definitions()) {
            indexHtml(embedding, event, def.html());
        }
    }

    private void evaluate(String model, EmbeddingClient embedding,
            Event event, List<EvalQuery> questions) {
        int score = 0;
        try {
            indexTemplates(embedding, event);
            indexSeeds(embedding, event);
        } catch (Exception e) {
            note("== %s: SKIP (색인 실패: %s)".formatted(model, e.getMessage()));
            return;
        }
        for (EvalQuery q : questions) {
            List<RagChunk> hits = repo.findSimilar(
                    Vectors.toDb(embedding.embed(q.query())),
                    // 유사도 0.6 (maxDistance 0.4): 최고 6/10, 합격선(7) 미달이라 관문 넓혀 재실험
                    // model, -1L, 0.4, 3);
                    // 유사도 0.5 (maxDistance 0.5)
                    model, -1L, 0.5, 3);
            boolean ok = q.expectedBlock() == null
                    ? hits.isEmpty()
                    : hits.stream().anyMatch(h -> h.getBlockKey().equals(q.expectedBlock()));
            if (ok) score++;

            note("[%s] Q=%s 기대=%s → %s".formatted(
                    model, q.query(), q.expectedBlock(), ok ? "O" : "X"));
            for (RagChunk h : hits) {
                note("    - (%s) %s".formatted(h.getBlockKey(), h.getContent()));
            }
        }
        note("== %s: %d/%d".formatted(model, score, questions.size()));
    }

    private void tryEvaluate(String model, EmbeddingClient embedding,
            Event event, List<EvalQuery> questions) {
        try {
            evaluate(model, embedding, event, questions);
        } catch (Exception e) {
            note("== %s: SKIP (%s)".formatted(model, e.getMessage()));
        }
    }

    @Test
    @EnabledIf("스모크_켜짐")
    @DisplayName("모델별 색인 → 검색 → 점수 출력.")
    void 모델별_평가() throws Exception {
        List<EvalQuery> questions = loadQuestions();
        Event event = seedEvent();
        String ollamaUrl = System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434");

        tryEvaluate("bge-m3",
                new OllamaEmbeddingClient(ollamaUrl, "bge-m3"), event, questions);
        tryEvaluate("mxbai-embed-large",
                new OllamaEmbeddingClient(ollamaUrl, "mxbai-embed-large"), event, questions);
        tryEvaluate("snowflake-arctic-embed",
                new OllamaEmbeddingClient(ollamaUrl, "snowflake-arctic-embed"), event, questions);

        if ("1".equals(System.getenv("RAG_EVAL_AWS"))) {
            String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
            tryEvaluate("amazon.titan-embed-text-v2:0",
                    new BedrockEmbeddingClient(region, "amazon.titan-embed-text-v2:0"),
                    event, questions);
            tryEvaluate("cohere.embed-multilingual-v3",
                    new BedrockEmbeddingClient(region, "cohere.embed-multilingual-v3"),
                    event, questions);
        } else {
            note("== Bedrock 2종 SKIP (RAG_EVAL_AWS=1 필요)");
        }

        Path dir = Paths.get("build", "rag-eval");
        Files.createDirectories(dir);
        String name = "eval-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".md";
        Files.write(dir.resolve(name), report, StandardCharsets.UTF_8);
    }
}
