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
import com.newvent.user.dto.request.ChangePlanRequest;
import com.newvent.user.dto.request.SignUpRequest;
import com.newvent.user.dto.request.UpdateUserRequest;
import com.newvent.user.dto.response.UserResponse;
import com.newvent.user.service.UserService;

/**
 *   POST  /api/public/users/signup   회원가입 (공개)
 *   GET   /api/users/me              내 정보       (USER)
 *   PATCH /api/users/me              내 정보 수정  (USER)
 *   PATCH /api/users/me/plan         요금제 변경   (USER)
 *
 * ★ 대상 회원은 경로의 id 가 아니라 토큰의 id 로 정한다 — 남의 id 를 넣어 조회·수정할 길이 없다.
 *   관리자 토큰의 id 는 admins 의 id 라 /me 에 오면 안 된다. SecurityConfig 가 USER 만 통과시킨다.
 */
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/api/public/users/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        User user = userService.signUp(
                request.loginId(), request.password(), request.name(), request.email(), request.phone(),
                request.plan());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(UserResponse.from(user)));
    }

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

    @PatchMapping("/api/users/me/plan")
    public ApiResponse<UserResponse> changeMyPlan(
            @AuthenticationPrincipal AuthUser principal, @Valid @RequestBody ChangePlanRequest request) {
        User user = userService.changePlan(principal.id(), request.plan());
        return ApiResponse.success(UserResponse.from(user));
    }
}
