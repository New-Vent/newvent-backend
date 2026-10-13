package com.newvent.generation.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.common.response.PageResponse;
import com.newvent.generation.dto.ChatMessageResponse;
import com.newvent.generation.service.ChatHistoryService;

import io.swagger.v3.oas.annotations.Operation;

// AI 채팅 내역 조회
@Validated
@RestController
@RequestMapping("/api/admin/events/{eventId}/chat-messages")
public class AdminChatMessageController {

    private final ChatHistoryService history;

    public AdminChatMessageController(ChatHistoryService history) {
        this.history = history;
    }

    @Operation(
            summary = "AI 채팅 내역",
            description = "이벤트의 대화를 오래된 것부터 시간순으로 돌려준다. 이벤트를 만든 관리자만 볼 수 있다. "
                    + "개인정보 후보가 감지된 요청은 원문 대신 \"[개인정보 포함 요청]\" 이 나온다. "
                    + "versionNo 는 그 요청으로 만들어진 버전의 번호이고, 버전을 만들지 않았으면 null 이다.")
    @GetMapping
    public ApiResponse<PageResponse<ChatMessageResponse>> list(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable Long eventId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ApiResponse.success(history.list(eventId, principal.id(), page, size));
    }
}
