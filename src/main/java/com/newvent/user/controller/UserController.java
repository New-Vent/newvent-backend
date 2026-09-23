package com.newvent.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.common.response.ApiResponse;
import com.newvent.user.domain.User;
import com.newvent.user.dto.request.ChangePlanRequest;
import com.newvent.user.dto.request.SignUpRequest;
import com.newvent.user.dto.request.UpdateUserRequest;
import com.newvent.user.dto.response.UserResponse;
import com.newvent.user.service.UserService;

@RestController
@RequestMapping("/api/public/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        User user = userService.signUp(
                request.loginId(), request.password(), request.name(), request.email(), request.phone(),
                request.plan());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(UserResponse.from(user)));
    }

    // TODO: 인증/인가 구현 후 본인(또는 관리자)만 조회 가능하도록 제한
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable Long id) {
        return ApiResponse.success(UserResponse.from(userService.getById(id)));
    }

    // TODO: 인증/인가 구현 후 본인만 수정 가능하도록 제한
    @PatchMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        User user = userService.updateProfile(id, request.name(), request.email(), request.phone());
        return ApiResponse.success(UserResponse.from(user));
    }

    // TODO: 인증/인가 구현 후 본인만 변경 가능하도록 제한
    @PatchMapping("/{id}/plan")
    public ApiResponse<UserResponse> changePlan(@PathVariable Long id, @Valid @RequestBody ChangePlanRequest request) {
        User user = userService.changePlan(id, request.plan());
        return ApiResponse.success(UserResponse.from(user));
    }
}
