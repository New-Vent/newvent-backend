package com.newvent.generation.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.newvent.common.response.ApiResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.service.TemplateLibraryService;
import com.newvent.generation.dto.GenerateRequest;
import com.newvent.generation.dto.GenerateStartResponse;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.GenerationException;
import com.newvent.generation.service.GenerateCommand;
import com.newvent.generation.service.GenerationService;
import com.newvent.generation.service.GenerationService.StartResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 생성 시작.
 *
 * ★ 실패는 예외로 던진다.
 *
 * ★ 응답에 원본 예외나 스택을 절대 담지 않는다 — `REQ-LLM-36`
 */
@Tag(name = "페이지 생성", description = "이벤트 페이지를 템플릿이나 AI 로 만든다. 생성은 비동기라 시작만 하고, "
        + "진행 상황은 페이지 생성 작업 API 로 폴링한다.")
@RestController
@RequestMapping("/api/admin/events/{eventId}/generate")
public class GenerateController {

    private final GenerationService generation;
    private final EventRepository events;
    private final TemplateLibraryService library;

    public GenerateController(GenerationService generation, EventRepository events,
            TemplateLibraryService library) {
        this.generation = generation;
        this.events = events;
        this.library = library;
    }

    @Operation(
            summary = "페이지 생성 시작",
            description = "202 와 jobId 를 돌려주고 생성은 서버에서 계속 돈다. 이벤트를 만든 관리자만 할 수 있다 (아니면 403 COMMON403-0). "
                    + "templateCode 를 보내지 않으면 이벤트에 붙은 템플릿으로, 빈 문자열이면 requestText 로 AI 가 만든다. "
                    + "템플릿 경로에서는 모델을 부르지 않는다. 둘을 같이 보내면 요청문이 버려지므로 400 (GEN400-3). "
                    + "AI 경로에서 requestText 가 비면 400 (GEN400-0), 500자를 넘으면 400. "
                    + "요청문에 개인정보가 보이면 바로 ASK_BACK 으로 끝나고, 확인한 뒤 privacyConfirmationJobId·privacyConfirmed 를 담아 "
                    + "다시 보낸다 (확인 요청이 없거나 만료 409 FILTER409-0, 내용이 다르면 409 FILTER409-2). "
                    + "이벤트가 없으면 404 (EVENT404-0), 템플릿이 없거나 고를 수 없으면 404 (EVENT404-1), "
                    + "종료된 이벤트는 409 (GEN409-1), 이 이벤트에서 생성·채팅 수정이 돌고 있으면 409 (GEN409-0), "
                    + "AI 경로의 일일 호출 상한을 넘으면 429 (LLM429-0)")
    @PostMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<GenerateStartResponse>> start(
            @PathVariable Long eventId,
            @Valid @RequestBody GenerateRequest req,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.newvent.auth.dto.AuthUser admin) {

        // ★ 삭제 필터를 쿼리가 한다. 지워진 행을 읽어 온 뒤 버리는 것보다 안 읽는 게 맞다.
        Event event = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        if (admin == null || !admin.admin() || event.getOwnerAdmin() == null
                || !admin.id().equals(event.getOwnerAdmin().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("이벤트 소유 관리자만 생성할 수 있습니다.");
        }
        GenerateCommand initial = GenerateCommand.of(event, req.templateCode(), req.requestText());
        if (initial.templateCode() != null) {
            library.checkSelection(admin.id(), initial.templateCode(), !initial.templateFromEvent());
        }
        GenerateCommand cmd = new GenerateCommand(initial.eventId(), initial.templateCode(), initial.templateFromEvent(),
                initial.title(), initial.period(), initial.ctaUrl(), initial.requestText(),
                req.privacyConfirmationJobId(), req.privacyConfirmed());

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
