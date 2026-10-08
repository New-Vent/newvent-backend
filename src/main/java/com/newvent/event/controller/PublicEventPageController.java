package com.newvent.event.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventNotFoundException;
import com.newvent.event.service.PublicEventService;
import com.newvent.registry.PageShell;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "공개 이벤트", description = "사용자 화면용. 로그인 없이 볼 수 있다.")
@RestController
public class PublicEventPageController {

    private final PublicEventService publicEventService;

    public PublicEventPageController(PublicEventService publicEventService) {
        this.publicEventService = publicEventService;
    }

    @Operation(
            summary = "이벤트 단독 페이지",
            description = "공유 링크용. JSON 이 아니라 게시 HTML 을 완성된 HTML 문서로 감싸 text/html 로 준다. "
                    + "열 수 있는 조건은 공개 이벤트 상세와 같다 (404 EVENT404-0 · EVENT404-2)")
    @GetMapping(value = "/e/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String page(@PathVariable Long id) {
        Event event = publicEventService.getPublicEvent(id);
        String html = publicEventService.publishedHtmlOf(event);
        if (html == null) {
            throw new EventNotFoundException();
        }
        return PageShell.standalone(html, event.getTitle());
    }
}
