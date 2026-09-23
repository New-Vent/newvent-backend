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
        String requestText) {

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
     * ★ **확인이 필요한 한 줄이다.**
     *   `events.url` 이 "참여 링크" 면 이대로 맞고, "게시된 페이지 주소" 면 틀렸다 —
     *   후자면 CTA 버튼이 자기 페이지를 다시 여는 꼴이 된다.
     *   이벤트 파트 답이 오면 **고칠 곳은 이 메서드 하나뿐이다.**
     *   (참여 링크 칼럼이 따로 없다는 답이 오면 여기서 null 을 돌려주고,
     *    버튼은 링크 없이 나간다 — 엉뚱한 데로 보내는 것보다 낫다.)
     */
    private static String ctaUrl(Event event) {
        String url = event.getUrl();
        return (url == null || url.isBlank()) ? null : url.strip();
    }

    /** 템플릿을 골랐나. 이 한 줄이 두 경로를 가른다 */
    public boolean hasTemplate() {
        return templateCode != null && !templateCode.isBlank();
    }
}
