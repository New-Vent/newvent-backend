package com.newvent.generation.service;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventTemplate;

/**
 * `new GenerateCommand(...)` 는 테스트에서만 쓴다.**
 *   서비스 경로는 전부 `of()` · `forRender()` 를 지난다.
 */
public record GenerateCommand(
        Long eventId,
        String templateCode,
        String title,
        String period,
        String ctaUrl,
        String requestText,
        java.util.UUID clarificationJobId, boolean privacyConfirmed) {
    public GenerateCommand(Long eventId, String templateCode, String title, String period,
                           String ctaUrl, String requestText, java.util.UUID clarificationJobId) {
        this(eventId, templateCode, title, period, ctaUrl, requestText, clarificationJobId, false);
    }
    public GenerateCommand(Long eventId, String templateCode, String title, String period,
                           String ctaUrl, String requestText) {
        this(eventId, templateCode, title, period, ctaUrl, requestText, null);
    }

    /**
     * 생성 시작. **트랜잭션 안에서 부른다** (아래 지연 로딩 주석 참고).
     *
     * @param requestedTemplateCode 이번 요청에서 고른 템플릿. null 이면 이벤트에 붙어 있는 걸 쓴다
     */
    public static GenerateCommand of(Event event, String requestedTemplateCode, String requestText) {
        return new GenerateCommand(
                event.getId(),
                templateCode(event, requestedTemplateCode),
                event.getTitle(),
                PeriodText.of(event.getStartDate(), event.getEndDate()),
                ctaUrl(event),
                requestText);
    }

    /**
     * 저장된 버전을 **보여주기만** 할 때. 템플릿 코드도 요청문도 필요 없다.
     *
     * ★ 슬롯 값(기간·참여링크)만 있으면 된다. `GenerationService.render()` 가 그것만 본다.
     */
    public static GenerateCommand forRender(Event event) {
        return new GenerateCommand(
                event.getId(), null, event.getTitle(),
                PeriodText.of(event.getStartDate(), event.getEndDate()),
                ctaUrl(event), null);
    }

    /**
     * ★ 요청이 고른 게 우선, 없으면 이벤트에 붙어 있는 것.
     */
    private static String templateCode(Event event, String requested) {
        if (requested != null && !requested.isBlank()) return requested.strip();

        EventTemplate t = event.getTemplate();
        return (t == null) ? null : t.getCode();
    }

    /**
     * CTA 버튼에 붙일 참여 링크. **지금은 담을 칸이 없어서 항상 null 이다.**
     *
     * ★ `events.url` 을 쓰면 안 된다 — 이벤트 파트 확인 결과 **게시 링크**다.
     *   CTA 에 넣으면 버튼이 자기 페이지를 다시 여는 꼴이 된다.
     *
     */
    private static String ctaUrl(Event event) {
        return null;
    }

    /** 템플릿을 골랐나. 이 한 줄이 두 경로를 가른다 */
    public boolean hasTemplate() {
        return templateCode != null && !templateCode.isBlank();
    }
}
