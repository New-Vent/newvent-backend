package com.newvent.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import com.newvent.auth.cookie.RefreshCookies;
import com.newvent.auth.dto.TokenResponse;
import com.newvent.auth.exception.InvalidRefreshTokenException;
import com.newvent.auth.service.AuthService;
import com.newvent.auth.web.AccountType;
import com.newvent.common.response.ApiResponse;

/**
 * 사용자 · 관리자 인증 컨트롤러가 같이 쓰는 처리. **계정 종류(AccountType)만 다르고 나머지는 같다.**
 *
 *   refresh  그 계정 종류의 쿠키로 재발급(Rotation). 쓸 수 없는 쿠키는 브라우저에서도 지운다
 *   logout   그 계정 종류의 쿠키를 폐기하고 만료시킨다 (204)
 *   발급     Access Token 은 본문, Refresh Token 은 그 계정 종류의 쿠키로
 *
 * ★ 여기에는 매핑(@PostMapping)을 두지 않는다. 경로는 각 컨트롤러가 AuthPaths 로 연다 —
 *   사용자 · 관리자 엔드포인트가 한 파일에서 한눈에 보이게.
 *
 * ★ 패키지 밖에서 쓰지 않는다 (package-private).
 */
abstract class AuthControllerSupport {

    protected final AuthService authService;
    private final RefreshCookies cookies;
    private final AccountType accountType;

    AuthControllerSupport(AuthService authService, RefreshCookies cookies, AccountType accountType) {
        this.authService = authService;
        this.cookies = cookies;
        this.accountType = accountType;
    }

    /** 요청에 실려 온 이 계정 종류의 Refresh 쿠키 — 재로그인 때 옛 토큰을 폐기하려고 넘긴다 */
    String currentRefreshToken(HttpServletRequest request) {
        return cookies.read(accountType, request);
    }

    ResponseEntity<ApiResponse<TokenResponse>> refreshWithCookie(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        try {
            return issue(authService.refresh(accountType, cookies.read(accountType, request)));
        } catch (InvalidRefreshTokenException e) {
            // 쓸 수 없는 쿠키는 브라우저에서도 지운다
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.expire(accountType).toString());
            throw e;
        }
    }

    ResponseEntity<Void> logoutAndExpireCookie(HttpServletRequest request) {
        authService.logout(accountType, cookies.read(accountType, request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expire(accountType).toString())
                .build();
    }

    /** Access Token 은 본문으로, Refresh Token 은 그 계정 종류의 쿠키로 내려보낸다 */
    ResponseEntity<ApiResponse<TokenResponse>> issue(AuthService.Issued issued) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(accountType, issued.refreshToken()).toString())
                .body(ApiResponse.success(issued.body()));
    }
}
