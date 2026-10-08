package com.newvent.generation.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.newvent.registry.Block;
import com.newvent.registry.BlockValidator;
import com.newvent.registry.PageShell;
import com.newvent.registry.Palette;
import com.newvent.registry.Slots;

/** 저장된 버전을 재사용한다. 실행 코드는 기본 제공 스크립트와 완전히 같은 것만 허용한다. */
public final class SavedTemplateHtml {
    private SavedTemplateHtml() {}

    private static class Approved {
        private static final Set<String> SCRIPTS = new ResourceTemplateLoader().all().stream()
                .flatMap(t -> parse(t.html()).select("script").stream())
                .map(Element::outerHtml).collect(Collectors.toUnmodifiableSet());
    }

    public static String clean(String html) {
        Document source = parse(html);
        List<String> scripts = source.select("script").stream()
                .map(Element::outerHtml).filter(Approved.SCRIPTS::contains).distinct().toList();
        Element root = PageShell.rootOf(source);
        List<String> palettes = root == null ? List.of() : root.classNames().stream()
                .filter(c -> Palette.find(c).isPresent()).limit(1).toList();

        Document cleaned = parse(BlockValidator.sanitizeEdited(html));
        // 유의사항은 현재 서버 승인본으로 다시 붙인다.
        cleaned.select(Block.NOTICES.selector()).remove();
        cleaned.select("meta, title, link, base").remove();
        for (String script : scripts) cleaned.body().append(script);
        String result = PageShell.plant(Slots.clear(cleaned.body().html()), null);
        Document out = parse(result);
        Element outRoot = PageShell.rootOf(out);
        palettes.forEach(outRoot::addClass);
        return out.body().html();
    }

    private static Document parse(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        return doc;
    }
}
