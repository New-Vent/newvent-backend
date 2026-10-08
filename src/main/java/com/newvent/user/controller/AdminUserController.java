package com.newvent.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.common.response.PageResponse;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.dto.request.ChangePlanRequest;
import com.newvent.user.dto.response.AdminUserDetailResponse;
import com.newvent.user.dto.response.AdminUserSummaryResponse;
import com.newvent.user.service.UserService;


// 등급은 직접 고치지 않고 관리자가 요금제를 변경해서 계산하도록
@Validated
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminUserSummaryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) MembershipGrade grade,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(userService.searchUsers(keyword, grade, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(AdminUserDetailResponse.from(userService.getById(id)));
    }

    @PatchMapping("/{id}/plan")
    public ApiResponse<AdminUserDetailResponse> changePlan(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable Long id,
            @Valid @RequestBody ChangePlanRequest request) {
        return ApiResponse.success(
                AdminUserDetailResponse.from(userService.changePlanByAdmin(principal.id(), id, request.plan())));
    }
}
