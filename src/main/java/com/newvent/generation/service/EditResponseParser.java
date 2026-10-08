package com.newvent.generation.service;

import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class EditResponseParser {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    private EditResponseParser() {}

    public record Parsed(String html, String changeSummary) {}

    public static Optional<Parsed> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String json = raw.strip();

        // 모델이 JSON을 코드블록으로 감싼 경우에는 바깥 코드블록만 제거한다.
        if (json.startsWith("```") && json.endsWith("```")) {
            int firstNewline = json.indexOf('\n');
            if (firstNewline < 0) {
                return Optional.empty();
            }
            json = json.substring(firstNewline + 1, json.length() - 3).strip();
        }

        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }

            JsonNode htmlNode = root.get("html");
            JsonNode summaryNode = root.get("changeSummary");

            if (htmlNode == null || !htmlNode.isTextual()
                || summaryNode == null || !summaryNode.isTextual()) {
                return Optional.empty();
            }

            String html = htmlNode.asText();
            String summary = summaryNode.asText().strip();

            if (html.isBlank() || summary.isBlank()) {
                return Optional.empty();
            }

            if (summary.contains("\n") || summary.contains("\r")
                || summary.codePointCount(0, summary.length()) > 100) {
                return Optional.empty();
            }

            return Optional.of(new Parsed(html, summary));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
