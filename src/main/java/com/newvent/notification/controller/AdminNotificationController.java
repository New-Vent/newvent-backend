package com.newvent.notification.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.common.response.PageResponse;
import com.newvent.notification.dto.response.AdminNotificationResponse;
import com.newvent.notification.service.AdminNotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "관리자 알림", description = "이벤트 시작·종료 임박·종료 때 이벤트를 만든 관리자에게 쌓이는 알림. 내 알림만 보인다.")
@Validated
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService notificationService;

    @Operation(summary = "알림 목록", description = "내 알림을 최신순으로 페이지 단위로 준다. "
            + "eventId 를 주면 그 이벤트의 알림만 준다. page 는 0 이상, size 는 1~50 (아니면 400)")
    @GetMapping
    public ApiResponse<PageResponse<AdminNotificationResponse>> list(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(required = false) Long eventId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(notificationService.getNotifications(principal.id(), eventId, page, size));
    }

    @Operation(summary = "알림 읽음 처리", description = "알림을 읽음으로 바꾼다. 이미 읽은 알림이면 처음 읽은 시각을 그대로 둔다. "
            + "알림이 없거나 다른 관리자의 알림이면 404 (NOTI404-0)")
    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable Long id) {
        notificationService.markRead(principal.id(), id);
        return ApiResponse.successNoData();
    }
}
