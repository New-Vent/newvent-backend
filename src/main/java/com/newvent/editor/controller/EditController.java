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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 채팅 수정 시작.
 *
 * ★ SecurityConfig 의 관리자 권한 검사에 더해 이벤트 소유자를 확인한다.
 *
 * ★ 응답에 원본 예외나 스택을 절대 담지 않는다
 */
@Tag(name = "채팅 수정", description = "만들어진 이벤트 페이지를 요청문으로 고친다. 수정은 비동기라 시작만 하고, "
        + "진행 상황은 받은 jobId 로 페이지 생성 작업 API 를 폴링한다. "
        + "이벤트를 만든 관리자만 할 수 있다 (아니면 403 COMMON403-0)")
@RestController
@RequestMapping("/api/admin/events/{eventId}/edit")
public class EditController {

    private final EditService edit;
    private final EventRepository events;

    public EditController(EditService edit, EventRepository events) {
        this.edit = edit;
        this.events = events;
    }

    @Operation(
            summary = "채팅 수정 시작",
            description = "마지막 버전을 요청문대로 고치는 작업을 시작하고 202 로 jobId 를 준다. 성공하면 새 버전이 생긴다. "
                    + "blocks 로 고칠 영역을 고르면 그 영역만 고치고, 없으면 요청문으로 영역까지 정한다. "
                    + "요청문이 비면 400 (GEN400-0), 500자를 넘거나 영역이 네 개를 넘으면 400, "
                    + "고를 수 없는 영역이면 400 (EDIT400-0). "
                    + "무엇을 고칠지 알아내지 못하거나 요청문에 개인정보가 보이면 작업이 ASK_BACK 으로 끝나고, "
                    + "개인정보는 확인한 뒤 privacyConfirmationJobId·privacyConfirmed 를 실어 다시 보낸다 "
                    + "(확인 요청이 없거나 만료 409 FILTER409-0, 그 사이 버전이 바뀌면 409 FILTER409-1, 내용이 다르면 409 FILTER409-2). "
                    + "이벤트가 없으면 404 (EVENT404-0), 고칠 페이지가 아직 없으면 404 (EDIT404-0), "
                    + "종료된 이벤트는 409 (GEN409-1), 이 이벤트에서 생성·채팅 수정이 돌고 있으면 409 (EDIT409-0), "
                    + "일일 호출 상한을 넘으면 429 (LLM429-0)")
    @PostMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<EditStartResponse>> start(
            @PathVariable Long eventId,
            @Valid @RequestBody EditRequest req,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.newvent.auth.dto.AuthUser admin) {

        // ★ 삭제 필터를 쿼리가 한다. 지워진 행을 읽어 온 뒤 버리는 것보다 안 읽는 게 맞다
        Event event = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
        if (admin == null || !admin.admin() || event.getOwnerAdmin() == null
                || !admin.id().equals(event.getOwnerAdmin().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("이벤트 소유 관리자만 수정할 수 있습니다.");
        }
        EditCommand cmd = new EditCommand(event.getId(), event.getTitle(),
                req.requestText(), req.blocks(), req.privacyConfirmationJobId(), req.privacyConfirmed());

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
