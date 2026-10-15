package com.newvent.generation.service;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.newvent.registry.ApprovedScripts;
import com.newvent.registry.BehaviorPlanter;
import com.newvent.registry.BlockValidator;
import com.newvent.registry.PageShell;
import com.newvent.registry.Palette;
import com.newvent.registry.Slots;

/** 저장된 버전을 재사용한다. 실행 코드는 기본 제공 스크립트와 완전히 같은 것만 허용한다. */
public final class SavedTemplateHtml {
    private SavedTemplateHtml() {}

    public static String clean(String html) {
        Document source = parse(html);
        // ★ 승인 목록은 registry.ApprovedScripts 가 한 곳에 들고 있다 — PageShell 과 같은 것을 본다
        List<String> scripts = source.select("script").stream()
                .map(Element::outerHtml).filter(ApprovedScripts.SCRIPTS::contains).distinct().toList();
        Element root = PageShell.rootOf(source);
        List<String> palettes = root == null ? List.of() : root.classNames().stream()
                .filter(c -> Palette.find(c).isPresent()).limit(1).toList();

        Document cleaned = parse(BlockValidator.sanitizeEdited(html));
        // 관리자 직접 편집한 유의사항도 템플릿에 보존한다. 없는 경우 PageShell이 기본 문구를 넣는다.
        cleaned.select("meta, title, link, base").remove();
        for (String script : scripts) cleaned.body().append(script);
        // ★ 참여 이름표를 보장한다 — 등록 당시 조각이 어느 경로를 지났든(백지 · 템플릿 · 더미)
        //   저장되는 템플릿에는 심긴 채로 들어간다. 이걸로 만든 이벤트가 자동으로 열린다
        String result = PageShell.plant(
                BehaviorPlanter.ensureParticipate(Slots.clear(cleaned.body().html())), null);
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
