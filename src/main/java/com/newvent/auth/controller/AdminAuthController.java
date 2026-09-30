package com.newvent.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.newvent.auth.cookie.RefreshCookies;
import com.newvent.auth.dto.LoginRequest;
import com.newvent.auth.dto.TokenResponse;
import com.newvent.auth.service.AuthService;
import com.newvent.auth.web.AccountType;
import com.newvent.auth.web.AuthPaths;
import com.newvent.common.response.ApiResponse;

/**
 * 관리자(admins) 인증.
 *
 *   POST /api/admin/auth/login     로그인 — Refresh 쿠키 nv_admin_rt (Path=/api/admin/auth)
 *   POST /api/admin/auth/refresh   Refresh 쿠키로 재발급 (Rotation)
 *   POST /api/admin/auth/logout    Refresh 쿠키 폐기 (204)
 *
 * ★ /api/admin/** 아래지만 토큰 없이 부를 수 있다 — SecurityConfig 가 이 세 경로를
 *   관리자 권한 규칙보다 앞에서 연다 (AccountType.publicPaths()).
 *
 * 비활성(is_active = false) 관리자는 비밀번호가 맞아도 로그인 · 재발급을 거부한다 (AuthServiceImpl).
 */
@RestController
@RequestMapping(AuthPaths.ADMIN)
public class AdminAuthController extends AuthControllerSupport {

    public AdminAuthController(AuthService authService, RefreshCookies cookies) {
        super(authService, cookies, AccountType.ADMIN);
    }

    @PostMapping(AuthPaths.LOGIN)
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest servletRequest) {
        return issue(authService.loginAdmin(request.loginId(), request.password(),
                currentRefreshToken(servletRequest)));
    }

    @PostMapping(AuthPaths.REFRESH)
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(HttpServletRequest request,
                                                              HttpServletResponse response) {
        return refreshWithCookie(request, response);
    }

    @PostMapping(AuthPaths.LOGOUT)
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        return logoutAndExpireCookie(request);
    }
}
