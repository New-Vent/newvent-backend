package com.newvent.user.web;

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
import com.newvent.user.service.MemberService;

@RestController
@RequestMapping("/api/public/users")
public class UserController {

    private final MemberService memberService;

    public UserController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        User user = memberService.signUp(
                request.loginId(), request.password(), request.name(), request.email(), request.phone(),
                request.plan());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(UserResponse.from(user)));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable Long id) {
        return ApiResponse.success(UserResponse.from(memberService.getById(id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        User user = memberService.updateProfile(id, request.name(), request.email(), request.phone());
        return ApiResponse.success(UserResponse.from(user));
    }

    @PatchMapping("/{id}/plan")
    public ApiResponse<UserResponse> changePlan(@PathVariable Long id, @Valid @RequestBody ChangePlanRequest request) {
        User user = memberService.changePlan(id, request.plan());
        return ApiResponse.success(UserResponse.from(user));
    }
}
