package com.newvent.editor.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * 라우터 모델 출력에서 연산 목록을 꺼낸다.
 *
 * ★ 이 클래스의 유일한 책임은 "글자 → List&lt;RawRoute&gt;" 다.
 *
 * ★ 왜 배열인가 — **단수 스키마는 복합 요청을 못 담는다 (v11 측정)**
 *   "제목 바꾸고 혜택도 추가해줘" 에 {op,target} 하나만 시키면
 *   모델이 JSON 을 **두 개** 뱉는다. 스키마가 못 담으니 달리 방법이 없다.
 *   그걸 indexOf('{') ~ lastIndexOf('}') 로 자르면 파싱이 깨진다.
 *   모델 탓이 아니라 스키마 탓이고, 그래서 {"ops":[...]} 로 바꿨다.
 *
 * ★ 실패는 예외가 아니라 빈 값이다
 */
public final class RouteParser {

    /**
     * 한 요청에서 받아들일 연산 수.
     *
     * ★ 넘치면 **자르지 않고 실패**다.
     */
    public static final int MAX_OPS = 3;

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private RouteParser() {}

    /**
     * 못 읽으면 빈 값. 호출부는 그걸 보고 실패로 처리한다.
     *
     * ★ 세 가지 모양을 다 받는다
     *     {"ops":[{…},{…}]}    정상
     *     [{…},{…}]            래퍼를 빼먹은 것
     *     {"op":…,"target":…}  옛 단수 형태 — 하나짜리로 본다
     *   관대하게 받는 쪽이 낫다. 형태가 조금 달라서 실패하면 관리자만 손해다.
     *
     * ★ **빈 배열은 실패다.** "고칠 게 없다" 를 라우터가 말하게 두면
     *   호출부가 "성공했는데 아무 일도 안 일어남" 을 다뤄야 한다.
     */
    public static Optional<List<RawRoute>> parse(String raw) {
        String json = carve(raw);
        if (json == null) return Optional.empty();

        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            // 괄호는 맞췄는데 JSON 이 아닌 경우
            return Optional.empty();
        }

        JsonNode array = arrayOf(root);
        if (array == null) return Optional.empty();

        if (array.isEmpty() || array.size() > MAX_OPS) return Optional.empty();

        List<RawRoute> out = new ArrayList<>(array.size());
        for (JsonNode n : array) {
            if (!n.isObject()) return Optional.empty();
            out.add(new RawRoute(text(n, "op"), text(n, "target"), text(n, "content")));
        }
        return Optional.of(List.copyOf(out));
    }

    /** 루트에서 연산 배열을 찾는다. 못 찾으면 null */
    private static JsonNode arrayOf(JsonNode root) {
        if (root.isArray()) return root;                        // [{…}]

        if (root.isObject()) {
            JsonNode ops = root.get("ops");
            if (ops != null && ops.isArray()) return ops;        // {"ops":[{…}]}

            // 옛 단수 형태. 하나짜리 배열로 본다
            if (root.has("op") || root.has("target")) {
                return MAPPER.createArrayNode().add(root);
            }
        }
        return null;
    }

    /**
     * 앞뒤 군더더기를 버리고 JSON 덩어리만 남긴다.
     *
     * ★ 여는 괄호는 '{' 와 '[' 둘 다 본다.
     *
     * ★ 괄호를 세는 파서를 짜지 않는다.
     */
    private static String carve(String raw) {
        if (raw == null) return null;
        String t = raw.replace("```json", "").replace("```", "");

        int s = first(t, '{', '[');
        int e = last(t, '}', ']');
        return (s >= 0 && e > s) ? t.substring(s, e + 1) : null;
    }

    private static int first(String t, char a, char b) {
        int i = t.indexOf(a), j = t.indexOf(b);
        if (i < 0) return j;
        if (j < 0) return i;
        return Math.min(i, j);
    }

    private static int last(String t, char a, char b) {
        return Math.max(t.lastIndexOf(a), t.lastIndexOf(b));
    }

    /**
     * 없는 필드 · JSON null · 문자열 "null" ·
     * 빈 문자열을 전부 자바 null 로 만든다.
     */
    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || !v.isValueNode()) return null;

        String s = v.asText().trim();
        return (s.isEmpty() || s.equalsIgnoreCase("null")) ? null : s;
    }
}
