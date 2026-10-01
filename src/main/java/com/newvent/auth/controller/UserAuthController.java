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
 * 사용자(users) 인증.
 *
 *   POST /api/auth/login     로그인 — Refresh 쿠키 nv_user_rt (Path=/api/auth)
 *   POST /api/auth/refresh   Refresh 쿠키로 재발급 (Rotation)
 *   POST /api/auth/logout    Refresh 쿠키 폐기 (204)
 *
 * 관리자는 AdminAuthController. users / admins 의 login_id 는 테이블마다 따로 유일해서 엔드포인트를 나눈다.
 */
@RestController
@RequestMapping(AuthPaths.USER)
public class UserAuthController extends AuthControllerSupport {

    public UserAuthController(AuthService authService, RefreshCookies cookies) {
        super(authService, cookies, AccountType.USER);
    }

    @PostMapping(AuthPaths.LOGIN)
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest servletRequest) {
        return issue(authService.loginUser(request.loginId(), request.password(),
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
