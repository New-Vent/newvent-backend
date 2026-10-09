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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "관리자 인증", description = "관리자 로그인·토큰 재발급·로그아웃. /api/admin 아래지만 토큰 없이 부른다. "
        + "Access Token 은 응답 본문으로, Refresh Token 은 nv_admin_rt 쿠키 (Path=/api/admin/auth) 로 준다.")
@RestController
@RequestMapping(AuthPaths.ADMIN)
public class AdminAuthController extends AuthControllerSupport {

    public AdminAuthController(AuthService authService, RefreshCookies cookies) {
        super(authService, cookies, AccountType.ADMIN);
    }

    @Operation(summary = "관리자 로그인", description = "아이디·비밀번호로 로그인해 Access Token 과 Refresh 쿠키를 준다. "
            + "이미 Refresh 쿠키가 있으면 그 토큰은 폐기한다. 빈 값이면 400. "
            + "아이디·비밀번호가 틀리거나 비활성 관리자면 401 (AUTH401-0) 이고 사유는 구분하지 않는다.")
    @PostMapping(AuthPaths.LOGIN)
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest servletRequest) {
        return issue(authService.loginAdmin(request.loginId(), request.password(),
                currentRefreshToken(servletRequest)));
    }

    @Operation(summary = "관리자 토큰 재발급", description = "Refresh 쿠키로 새 Access Token 과 새 Refresh 쿠키를 준다. "
            + "쓴 Refresh Token 은 폐기한다 (Rotation). "
            + "쿠키가 없거나 만료·폐기됐거나 비활성 관리자면 401 (AUTH401-1) 이고 쿠키도 지운다. "
            + "이미 쓴 토큰이 다시 오면 탈취로 보고 그 관리자의 Refresh Token 을 모두 폐기한다. "
            + "Origin (없으면 Referer) 이 허용 목록에 없으면 403 (AUTH403-0)")
    @PostMapping(AuthPaths.REFRESH)
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(HttpServletRequest request,
                                                              HttpServletResponse response) {
        return refreshWithCookie(request, response);
    }

    @Operation(summary = "관리자 로그아웃", description = "Refresh 쿠키의 토큰을 폐기하고 쿠키를 만료시킨다. "
            + "쿠키가 없거나 이미 폐기된 토큰이어도 204. "
            + "Origin (없으면 Referer) 이 허용 목록에 없으면 403 (AUTH403-0)")
    @PostMapping(AuthPaths.LOGOUT)
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        return logoutAndExpireCookie(request);
    }
}
