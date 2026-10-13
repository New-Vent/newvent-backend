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
import io.swagger.v3.oas.annotations.tags.Tag;

// 요금제는 사용자가 바꾸지 못한다 - 가입 때는 서버 기본 요금제로 시작하고, 변경은 관리자 API 로만 한다.
@Tag(name = "회원", description = "회원가입과 내 정보 조회·수정. 회원가입은 토큰 없이 부른다. "
        + "내 정보는 사용자 토큰이 필요하고 관리자 토큰이면 403 (COMMON403-0)")
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "회원가입",
            description = "요금제는 받지 않는다(plan 을 보내도 무시). 모든 가입자가 서버 기본 요금제로 시작해 등급은 NORMAL 이다. "
                    + "요금제 변경은 관리자만 한다. 형식이 맞지 않으면 400, "
                    + "아이디가 이미 있으면 409 (USER409-0), 이메일이 이미 있으면 409 (USER409-1)")
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

    @Operation(
            summary = "내 정보 수정",
            description = "보낸 필드만 바꾼다(생략하면 그대로). 이메일이 다른 회원과 대소문자만 달라도 중복이라 409 (USER409-1). "
                    + "본인 이메일의 대소문자만 바꾸는 것은 허용한다. 값을 공백만으로 보내거나 형식이 맞지 않으면 400")
    @PatchMapping("/api/users/me")
    public ApiResponse<UserResponse> updateMe(
            @AuthenticationPrincipal AuthUser principal, @Valid @RequestBody UpdateUserRequest request) {
        User user = userService.updateProfile(principal.id(), request.name(), request.email(), request.phone());
        return ApiResponse.success(UserResponse.from(user));
    }
}
