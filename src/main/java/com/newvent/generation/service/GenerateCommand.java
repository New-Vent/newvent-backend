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
     * 이번 생성이 쓸 템플릿 코드. <b>null 이면 백지 생성이다.</b>
     *
     * ★ null 과 "" 를 구분한다
     *     null   미지정 → 이벤트에 붙어 있는 템플릿으로 폴백한다.
     *            "같은 템플릿으로 다시 생성" 을 코드 없이 부를 수 있게 하는 길이다.
     *     ""     <b>백지를 명시</b> → 폴백하지 않는다.
     *
     * ★ 왜 "" 가 필요한가 — <b>안 그러면 백지를 고를 방법이 없다</b>
     *   이벤트를 만들 때 templateKey 를 고를 수 있다(EventCreateRequest).
     *   그런 이벤트에서 "새로 만들기" 로 requestText 만 보내면 폴백이 걸려
     *   템플릿 경로로 가고, <b>요청문은 통째로 버려진다.</b>
     *   모델을 안 부르니 관리자는 AI 가 만든 줄 알고, 일일 상한에도 안 잡힌다.

     */
    private static String templateCode(Event event, String requested) {
        if (requested == null) {
            EventTemplate t = event.getTemplate();
            return (t == null) ? null : t.getCode();
        }
        return requested.isBlank() ? null : requested.strip();
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
