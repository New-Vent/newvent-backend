package com.newvent.generation.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 기본 템플릿 5종을 classpath 에서 읽는다.
 *
 *   src/main/resources/templates/
 *     templates.json                       메타데이터
 *     template_1_sports_cheer.html         본문
 *     ...
 *
 * ★ 기동할 때 한 번 다 읽어서 들고 있는다.
 *   5개 45KB 라 메모리가 문제되지 않고, 매 요청마다 파일을 여는 게 더 비싸다.
 *
 * ★ 여기 있는 게 DB 의 시드 원본이기도 하다.
 *   DbTemplateLoader 로 갈아끼워도 이 파일들은 남는다 —
 *   event_templates 에 기본 5종을 넣어주는 쪽이 여기서 읽는다.
 */
@Component
public class ResourceTemplateLoader implements TemplateLoader {

    private static final String DIR = "templates/";
    private static final String INDEX = DIR + "templates.json";

    /** code → Source. 넣은 순서(= templates.json 순서)를 유지한다 */
    private final Map<String, Source> byCode;

    public ResourceTemplateLoader() {
        this.byCode = readAll();
    }

    @Override
    public List<Source> all() {
        return List.copyOf(byCode.values());
    }

    @Override
    public Optional<Source> find(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    // ──────────────────────────────────────────────────────────────

    private static Map<String, Source> readAll() {
        JsonNode index;
        try {
            index = new ObjectMapper().readTree(text(INDEX));
        } catch (IOException e) {
            throw new IllegalStateException(INDEX + " 를 읽지 못했습니다.", e);
        }

        Map<String, Source> out = new LinkedHashMap<>();
        for (JsonNode n : index) {
            String code = n.path("code").asText("");
            if (code.isBlank()) {
                throw new IllegalStateException(INDEX + " 에 code 가 없는 항목이 있습니다: " + n);
            }
            String html = text(DIR + code + ".html");
            out.put(code, new Source(
                    code,
                    n.path("name").asText(code),
                    n.path("description").asText(""),
                    n.path("thumbnailPath").asText(null),
                    html,
                    true));            // ★ 기본 5종은 전부 builtin
        }
        if (out.isEmpty()) {
            throw new IllegalStateException(INDEX + " 가 비어 있습니다.");
        }
        return out;
    }

    private static String text(String path) {
        try (InputStream in = ResourceTemplateLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                // ★ 여기서 죽는 게 낫다. 템플릿이 없으면 템플릿 경로가 통째로 안 도는데,
                //   기동은 되고 첫 요청에서 터지면 원인을 찾기 훨씬 어렵다.
                throw new IllegalStateException(
                        path + " 가 없습니다. templates.json 의 code 와 파일명이 같은지 보세요.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(path + " 를 읽지 못했습니다.", e);
        }
    }
}
