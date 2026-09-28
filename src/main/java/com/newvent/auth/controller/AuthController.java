package com.newvent.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.cookie.RefreshCookies;
import com.newvent.auth.dto.LoginRequest;
import com.newvent.auth.dto.TokenResponse;
import com.newvent.auth.exception.InvalidRefreshTokenException;
import com.newvent.auth.service.AuthService;
import com.newvent.common.response.ApiResponse;

/**
 *   POST /auth/login        사용자(users) 로그인
 *   POST /auth/admin/login  관리자(admins) 로그인
 *   POST /auth/refresh      Refresh 쿠키로 재발급 (Rotation)
 *   POST /auth/logout       Refresh 쿠키 폐기 (204)
 *
 * users / admins 의 login_id 는 테이블마다 따로 유일하므로 로그인 엔드포인트를 나눈다.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookies cookies;

    public AuthController(AuthService authService, RefreshCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.loginUser(request.loginId(), request.password()));
    }

    @PostMapping("/admin/login")
    public ResponseEntity<ApiResponse<TokenResponse>> adminLogin(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.loginAdmin(request.loginId(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            return withRefreshCookie(authService.refresh(cookies.read(request)));
        } catch (InvalidRefreshTokenException e) {
            // 쓸 수 없는 쿠키는 브라우저에서도 지운다
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.expire().toString());
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(cookies.read(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expire().toString())
                .build();
    }

    private ResponseEntity<ApiResponse<TokenResponse>> withRefreshCookie(AuthService.Issued issued) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(issued.refreshToken()).toString())
                .body(ApiResponse.success(issued.body()));
    }
}
