package com.newvent.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.ApiResponse;
import com.newvent.user.domain.User;
import com.newvent.user.dto.request.SignUpRequest;
import com.newvent.user.dto.request.UpdateUserRequest;
import com.newvent.user.dto.response.UserResponse;
import com.newvent.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;

// 요금제는 사용자가 바꾸지 못한다 - 가입 때는 서버 기본 요금제로 시작하고, 변경은 관리자 API 로만 한다.
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "회원가입",
            description = "요금제는 받지 않는다(plan 을 보내도 무시). 모든 가입자가 서버 기본 요금제로 시작해 등급은 NORMAL 이다. "
                    + "요금제 변경은 관리자만 한다.")
    @PostMapping("/api/public/users/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        User user = userService.signUp(
                request.loginId(), request.password(), request.name(), request.email(), request.phone());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(UserResponse.from(user)));
    }

    @Operation(summary = "내 정보", description = "요금제·등급·가입일(joinedAt)을 돌려준다. 사용자는 조회만 할 수 있다.")
    @GetMapping("/api/users/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.success(UserResponse.from(userService.getById(principal.id())));
    }

    @PatchMapping("/api/users/me")
    public ApiResponse<UserResponse> updateMe(
            @AuthenticationPrincipal AuthUser principal, @Valid @RequestBody UpdateUserRequest request) {
        User user = userService.updateProfile(principal.id(), request.name(), request.email(), request.phone());
        return ApiResponse.success(UserResponse.from(user));
    }
}
