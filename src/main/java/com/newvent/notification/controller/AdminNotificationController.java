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

import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<AdminNotificationResponse>> list(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(required = false) Long eventId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(notificationService.getNotifications(principal.id(), eventId, page, size));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable Long id) {
        notificationService.markRead(principal.id(), id);
        return ApiResponse.successNoData();
    }
}
