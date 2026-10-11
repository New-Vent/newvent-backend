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

import io.swagger.v3.oas.annotations.Operation;


// 등급은 직접 고치지 않고 관리자가 요금제를 변경해서 계산하도록
@Validated
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "회원 목록",
            description = "keyword 는 아이디·이름·이메일에 포함되는 글자(대소문자 무시, % _ 도 글자 그대로). "
                    + "grade 로 거를 수 있고, 최근 가입순이다. 비밀번호 해시는 내려가지 않는다.")
    @GetMapping
    public ApiResponse<PageResponse<AdminUserSummaryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) MembershipGrade grade,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(userService.searchUsers(keyword, grade, page, size));
    }

    @Operation(summary = "회원 상세", description = "목록 항목에 전화번호와 가입·수정 시각이 더해진다. 없으면 404 (USER404-0)")
    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(AdminUserDetailResponse.from(userService.getById(id)));
    }

    @Operation(
            summary = "회원 요금제 변경",
            description = "관리자만 할 수 있다. 가입일은 두고 바뀐 요금제로 등급을 바로 다시 계산한다(내리면 등급도 내려간다). "
                    + "누가 무엇을 바꿨는지는 서버 로그(INFO)에 남는다.")
    @PatchMapping("/{id}/plan")
    public ApiResponse<AdminUserDetailResponse> changePlan(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable Long id,
            @Valid @RequestBody ChangePlanRequest request) {
        return ApiResponse.success(
                AdminUserDetailResponse.from(userService.changePlanByAdmin(principal.id(), id, request.plan())));
    }
}
