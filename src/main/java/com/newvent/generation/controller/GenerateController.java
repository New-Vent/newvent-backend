package com.newvent.generation.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.newvent.common.response.ApiResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.repository.EventRepository;
import com.newvent.generation.dto.GenerateRequest;
import com.newvent.generation.dto.GenerateStartResponse;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.GenerationException;
import com.newvent.generation.service.GenerateCommand;
import com.newvent.generation.service.GenerationService;
import com.newvent.generation.service.GenerationService.StartResult;

/**
 * 생성 시작.
 *
 * ★ 실패는 예외로 던진다.
 *
 * ★ 응답에 원본 예외나 스택을 절대 담지 않는다 — `REQ-LLM-36`
 */
@RestController
@RequestMapping("/api/admin/events/{eventId}/generate")
public class GenerateController {

    private final GenerationService generation;
    private final EventRepository events;

    public GenerateController(GenerationService generation, EventRepository events) {
        this.generation = generation;
        this.events = events;
    }

    @PostMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<GenerateStartResponse>> start(
            @PathVariable Long eventId,
            @Valid @RequestBody GenerateRequest req) {

        Event event = events.findById(eventId)
                .filter(e -> e.getDeletedAt() == null)   // 지워진 건 없는 것으로 본다
                .orElseThrow(() -> new GenerationException(GenerationErrorCode.EVENT_NOT_FOUND));

        GenerateCommand cmd = GenerateCommand.of(event, req.templateCode(), req.requestText());

        return switch (generation.start(cmd)) {
            case StartResult.Started s -> ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(GenerateStartResponse.of(s.job())));

            // 요청이 잘못됐다 — 고쳐서 다시 보내면 된다
            case StartResult.Rejected r -> throw new GenerationException(r.errorCode());

            // ★ 409 다. 400 이 아니다 — 요청은 멀쩡한데 지금은 안 될 뿐이다.
            case StartResult.AlreadyRunning ignored ->
                    throw new GenerationException(GenerationErrorCode.ALREADY_GENERATING);
        };
    }
}
