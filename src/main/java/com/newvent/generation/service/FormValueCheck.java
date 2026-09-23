package com.newvent.generation.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 생성 결과가 **폼 값과 어긋나지 않는가**. 
 *
 * ★ 지금은 날짜 하나만 본다. 그리고 그게 유일하게 확실히 잴 수 있는 것이다.
 *
 *   제목은 못 잰다 — hero 의 역할이 "제목과 한 줄 소개" 라서 모델이 문구를 다듬는 게
 *   정상이다. "여름 이벤트" → "여름 데이터 대방출" 을 틀렸다고 할 수 없다.
 *
 *   혜택은 아직 못 잰다 — events 에 혜택을 담는 컬럼이 없어서 **대조할 상대가 없다.**
 *   기획이 "혜택을 항목 배열로 받는다" 로 정하면 그때 개수·내용 대조가 가능해진다.
 *
 *   날짜는 잰다 — **기간은 슬롯 하나로만 존재해야 하기 때문이다.**
 *   프롬프트에 날짜를 아예 안 주는데 본문에 날짜가 있으면 모델이 지어낸 것이다.
 *   그게 게시되면 폼에서 기간을 고쳐도 그 날짜는 안 바뀐다.
 *
 */
public final class FormValueCheck {

    /**
     * 연도가 붙은 날짜 — 2026.07.01 · 2026-7-1 · 2026 / 07 / 01
     *
     * ★ 연도를 요구한다. 그래야 "10GB" · "3단계" 같은 숫자에 안 걸린다.
     */
    private static final Pattern YMD = Pattern.compile(
            "\\b(19|20)\\d{2}\\s*[.\\-/]\\s*\\d{1,2}\\s*[.\\-/]\\s*\\d{1,2}\\b");

    /** 한글 날짜 — 7월 1일 · 07 월 01 일 */
    private static final Pattern MD_KO = Pattern.compile(
            "\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일");

    private FormValueCheck() {}

    /**
     * 본문에서 지어낸 날짜를 찾는다.
     *
     * ★ [data-slot] 안은 안 본다. **서버가 채우는 자리라 날짜가 있는 게 정상이다.**
     */
    public static List<String> leakedDates(String html) {
        if (html == null || html.isBlank()) return List.of();

        Document doc = Jsoup.parseBodyFragment(html);
        doc.body().select("[data-slot]").forEach(org.jsoup.nodes.Element::remove);
        String text = doc.body().text();

        List<String> found = new ArrayList<>();
        collect(YMD, text, found);
        collect(MD_KO, text, found);
        return List.copyOf(found);
    }

    private static void collect(Pattern p, String text, List<String> out) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            String hit = m.group().strip();
            if (!out.contains(hit)) out.add(hit);
        }
    }
}
