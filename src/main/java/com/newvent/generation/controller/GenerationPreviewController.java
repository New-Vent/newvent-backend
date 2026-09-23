package com.newvent.generation.controller;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.newvent.common.response.ApiResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.repository.EventRepository;
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

    public GenerationPreviewController(GenerationService generation, EventRepository events) {
        this.generation = generation;
        this.events = events;
    }

    /**
     * 저장된 마지막 버전에 이벤트 값을 채워 돌려준다.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<PreviewResponse> preview(@PathVariable Long eventId) {

        Event event = events.findById(eventId)
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> new GenerationException(GenerationErrorCode.EVENT_NOT_FOUND));

        return generation.render(GenerateCommand.forRender(event))
                .map(html -> ApiResponse.success(PreviewResponse.of(html)))
                .orElseThrow(() -> new GenerationException(GenerationErrorCode.PAGE_NOT_FOUND));
    }
}
