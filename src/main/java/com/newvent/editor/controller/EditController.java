package com.newvent.editor.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.editor.dto.EditRequest;
import com.newvent.editor.dto.EditStartResponse;
import com.newvent.editor.exception.EditErrorCode;
import com.newvent.editor.exception.EditException;
import com.newvent.editor.service.EditCommand;
import com.newvent.editor.service.EditService;
import com.newvent.editor.service.EditService.StartResult;
import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;

/**
 * 채팅 수정 시작.
 *
 * ★ 권한은 SecurityConfig 가 본다 — {@code /api/admin/**} 은 ROLE_ADMIN 이다.
 *   여기서 다시 확인하지 않는다.
 *
 * ★ 응답에 원본 예외나 스택을 절대 담지 않는다
 */
@RestController
@RequestMapping("/api/admin/events/{eventId}/edit")
public class EditController {

    private final EditService edit;
    private final EventRepository events;

    public EditController(EditService edit, EventRepository events) {
        this.edit = edit;
        this.events = events;
    }

    @PostMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<EditStartResponse>> start(
            @PathVariable Long eventId,
            @Valid @RequestBody EditRequest req) {

        // ★ 삭제 필터를 쿼리가 한다. 지워진 행을 읽어 온 뒤 버리는 것보다 안 읽는 게 맞다
        Event event = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        EditCommand cmd = EditCommand.of(event, req.requestText());

        return switch (edit.start(cmd)) {
            case StartResult.Started s -> ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(EditStartResponse.of(s.job())));

            // 요청이 잘못됐다 — 고쳐서 다시 보내면 된다
            case StartResult.Rejected r -> throw new EditException(r.errorCode());

            // ★ 409 다. 요청은 멀쩡한데 지금은 안 될 뿐이다.
            //   생성과 자리를 공유하므로 "생성 중" 일 때도 여기로 온다
            case StartResult.AlreadyRunning ignored ->
                    throw new EditException(EditErrorCode.ALREADY_RUNNING);
        };
    }
}
