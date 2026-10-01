package com.newvent.generation.service;

import com.newvent.event.domain.Event;

/**
 * `new GenerateCommand(...)` 는 테스트에서만 쓴다.**
 *   서비스 경로는 전부 `of()` · `forRender()` 를 지난다.
 */
public record GenerateCommand(
        Long eventId,
        String templateCode,
        boolean templateFromEvent,
        String title,
        String period,
        String ctaUrl,
        String requestText,
        java.util.UUID privacyConfirmationJobId, boolean privacyConfirmed) {
    public GenerateCommand(Long eventId, String templateCode, String title, String period,
                           String ctaUrl, String requestText, java.util.UUID privacyConfirmationJobId) {
        this(eventId, templateCode, title, period, ctaUrl, requestText, privacyConfirmationJobId, false);
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
        String code = templateCode(event, requestedTemplateCode);
        return new GenerateCommand(
                event.getId(),
                code,
                // ★ 코드를 요청이 준 게 아니라 이벤트에서 가져왔는가.
                //   거부 로그에서 "프론트가 안 보냈다" 와 "프론트가 둘 다 보냈다" 를 가른다.
                //   이 둘은 프론트에서 고칠 곳이 다르다.
                requestedTemplateCode == null && code != null,
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
                event.getId(), null, false, event.getTitle(),
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
     *   템플릿 경로로 간다.
     *
     * ★ 그래도 요청문이 버려지지는 않는다 — {@link #discardsRequestText()} 가 막는다.
     *   폴백 자체는 남겨둔다. 코드 없이 "같은 템플릿으로 다시" 를 부르는 길이고,
     *   요청문이 없을 때는 버릴 것도 없어서 해로울 게 없다.
     */
    private static String templateCode(Event event, String requested) {
        if (requested == null) {
            return event.templateCode();
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

    /**
     * 요청문이 들어왔나.
     *
     * ★ 공백만 있는 건 없는 것으로 본다. RequestFilter.reject 와 같은 기준이어야
     *   "백지인데 요청문이 없다(EMPTY_REQUEST)" 와 여기의 판정이 어긋나지 않는다.
     */
    public boolean hasRequestText() {
        return requestText != null && !requestText.isBlank();
    }

    /**
     * 템플릿 경로로 가면서 요청문을 들고 있나. <b>그러면 요청문이 통째로 버려진다.</b>
     */
    public boolean discardsRequestText() {
        return hasTemplate() && hasRequestText();
    }
}
