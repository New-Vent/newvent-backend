package com.newvent.generation.controller;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.newvent.common.response.ApiResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.service.PublicEventService;
import com.newvent.generation.dto.PreviewResponse;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.GenerationException;
import com.newvent.generation.service.GenerateCommand;
import com.newvent.generation.service.GenerationService;


/**
 * 만들어진 페이지를 본다. **editor 가 준비될 때까지 쓰는 임시 엔드포인트다.**
 *
 */
@RestController
@RequestMapping("/api/admin/events/{eventId}/preview")
public class GenerationPreviewController {

    private final GenerationService generation;
    private final EventRepository events;
    private final PublicEventService pages;

    public GenerationPreviewController(GenerationService generation, EventRepository events,
                                       PublicEventService pages) {
        this.generation = generation;
        this.events = events;
        this.pages = pages;
    }

    /**
     * 저장된 마지막 버전에 이벤트 값을 채워 돌려준다.
     *
     * ★ 응답에 versionId 가 같이 나간다. 프론트가 이후 수정의 기준으로 쓴다.
     * ★ html 은 완전한 문서다 — 공개 페이지와 같은 PageShell.standalone
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<PreviewResponse> preview(@PathVariable Long eventId) {

        Event event = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        return generation.render(GenerateCommand.forRender(event))
                .map(rendered -> ApiResponse.success(PreviewResponse.of(rendered,
                        pages.pageOf(event, rendered.html()))))
                .orElseThrow(() -> new GenerationException(GenerationErrorCode.PAGE_NOT_FOUND));
    }
}
