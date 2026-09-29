package com.newvent.registry;

import java.util.Objects;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Range;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

/**
 * 원본 HTML에서 data-block 섹션을 정확히 한 개 찾아 꺼내거나 교체한다.
 * Jsoup은 위치와 구조 확인에만 쓰고, 문서 전체를 직렬화하지 않는다.
 * 그래야 다른 섹션과 그 밖의 CSS·스크립트·공백이 원본 그대로 남는다.
 */
public final class BlockMerge {

    private BlockMerge() {}

    public static String extract(String html, Block block) {
        Span span = locate(html, block);
        return html.substring(span.start(), span.end());
    }

    public static String merge(String html, Block block, String newSec) {
        Span old = locate(html, block);
        Objects.requireNonNull(newSec, "newSec");

        // 다른 블록이나 설명문이 끼어 있는 모델 출력은 조용히 버리지 않는다.
        String replacement = extract(newSec, block);
        if (!replacement.equals(newSec.strip())) {
            throw new IllegalArgumentException(block.key() + " 섹션 하나만 전달해야 합니다.");
        }

        return html.substring(0, old.start()) + replacement + html.substring(old.end());
    }

    private static Span locate(String html, Block block) {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(block, "block");

        Document doc = Jsoup.parse(html, "", Parser.htmlParser().setTrackPosition(true));
        // ★ 태그 이름을 붙이지 않는다 — 검증기와 같은 문자열을 봐야 한다.
        Elements matches = doc.select(block.selector());
        if (matches.size() != 1) {
            throw new IllegalArgumentException(block.key() + " 섹션은 정확히 하나여야 합니다. 발견: "
                    + matches.size());
        }

        Element section = matches.first();
        Range start = section.sourceRange();
        Range end = section.endSourceRange();
        if (!start.isTracked() || !end.isTracked()
                || start.startPos() < 0 || end.endPos() > html.length()
                || start.startPos() >= end.endPos()) {
            throw new IllegalArgumentException(block.key() + " 섹션의 시작·종료 태그를 확인할 수 없습니다.");
        }
        return new Span(start.startPos(), end.endPos());
    }

    private record Span(int start, int end) {}
}
