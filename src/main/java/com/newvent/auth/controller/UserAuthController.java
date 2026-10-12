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
 * 사용자(users) 인증.
 *
 *   POST /api/auth/login     로그인 — Refresh 쿠키 nv_user_rt (Path=/api/auth)
 *   POST /api/auth/refresh   Refresh 쿠키로 재발급 (Rotation)
 *   POST /api/auth/logout    Refresh 쿠키 폐기 (204)
 *
 * 관리자는 AdminAuthController. users / admins 의 login_id 는 테이블마다 따로 유일해서 엔드포인트를 나눈다.
 */
@Tag(name = "사용자 인증", description = "사용자 로그인·토큰 재발급·로그아웃. 토큰 없이 부른다. "
        + "Access Token 은 응답 본문으로, Refresh Token 은 nv_user_rt 쿠키 (Path=/api/auth) 로 준다.")
@RestController
@RequestMapping(AuthPaths.USER)
public class UserAuthController extends AuthControllerSupport {

    public UserAuthController(AuthService authService, RefreshCookies cookies) {
        super(authService, cookies, AccountType.USER);
    }

    @Operation(summary = "사용자 로그인", description = "아이디·비밀번호로 로그인해 Access Token 과 Refresh 쿠키를 준다. "
            + "로그인할 때 멤버십 등급을 다시 계산해 저장한다. 이미 Refresh 쿠키가 있으면 그 토큰은 폐기한다. 빈 값이면 400. "
            + "아이디·비밀번호가 틀리면 401 (AUTH401-0)")
    @PostMapping(AuthPaths.LOGIN)
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest servletRequest) {
        return issue(authService.loginUser(request.loginId(), request.password(),
                currentRefreshToken(servletRequest)));
    }

    @Operation(summary = "사용자 토큰 재발급", description = "Refresh 쿠키로 새 Access Token 과 새 Refresh 쿠키를 준다. "
            + "쓴 Refresh Token 은 폐기한다 (Rotation). "
            + "쿠키가 없거나 만료·폐기됐거나 삭제된 사용자면 401 (AUTH401-1) 이고 쿠키도 지운다. "
            + "이미 쓴 토큰이 다시 오면 탈취로 보고 그 사용자의 Refresh Token 을 모두 폐기한다. "
            + "Origin (없으면 Referer) 이 허용 목록에 없으면 403 (AUTH403-0)")
    @PostMapping(AuthPaths.REFRESH)
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(HttpServletRequest request,
                                                              HttpServletResponse response) {
        return refreshWithCookie(request, response);
    }

    @Operation(summary = "사용자 로그아웃", description = "Refresh 쿠키의 토큰을 폐기하고 쿠키를 만료시킨다. "
            + "쿠키가 없거나 이미 폐기된 토큰이어도 204. "
            + "Origin (없으면 Referer) 이 허용 목록에 없으면 403 (AUTH403-0)")
    @PostMapping(AuthPaths.LOGOUT)
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        return logoutAndExpireCookie(request);
    }
}
