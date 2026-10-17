package com.newvent.admin.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.admin.dto.response.AdminProfileResponse;
import com.newvent.admin.service.AdminProfileService;
import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(
    name = "관리자 정보",
    description = "로그인한 관리자 본인 정보 조회. 관리자 Access Token이 필요합니다."
)
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminProfileController {

    private final AdminProfileService adminProfileService;

    @Operation(
        summary = "관리자 본인 정보 조회",
        description = "Access Token의 관리자 ID로 본인 정보를 조회합니다. "
            + "토큰이 없거나 유효하지 않은 계정이면 401 (COMMON401-0), "
            + "사용자 토큰으로 요청하면 403 (COMMON403-0)을 반환합니다."
    )
    @GetMapping("/me")
    public ApiResponse<AdminProfileResponse> getMe(@AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.success(adminProfileService.getMyProfile(principal.id()));
    }
}
