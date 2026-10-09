package com.newvent.generation.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EditResponseParser {

    private static final Pattern SUMMARY_COMMENT = Pattern.compile(
        "\\s*<!--\\s*changeSummary\\s*:\\s*(.*?)-->\\s*\\z",
        Pattern.DOTALL);

    private EditResponseParser() {
    }

    public record Parsed(String html, String changeSummary) {
    }

    public static Optional<Parsed> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String html = raw.strip();

        // HTML 코드블록으로 감싼 응답도 허용한다.
        if (html.startsWith("```") && html.endsWith("```")) {
            int firstNewline = html.indexOf('\n');
            if (firstNewline < 0) {
                return Optional.empty();
            }
            html = html.substring(firstNewline + 1, html.length() - 3).strip();
        }

        String summary = null;
        Matcher matcher = SUMMARY_COMMENT.matcher(html);

        if (matcher.find()) {
            summary = normalizeSummary(matcher.group(1));
            html = html.substring(0, matcher.start()).strip();
        }

        if (html.isBlank()) {
            return Optional.empty();
        }

        // HTML 유효성 검사는 RetryService의 HtmlPolicy가 담당한다.
        return Optional.of(new Parsed(html, summary));
    }

    private static String normalizeSummary(String value) {
        String summary = value.replaceAll("\\s+", " ").strip();

        if (summary.isBlank()
            || summary.codePointCount(0, summary.length()) > 100) {
            return null;
        }

        return summary;
    }
}
