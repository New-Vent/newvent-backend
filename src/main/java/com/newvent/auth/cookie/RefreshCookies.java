package com.newvent.auth.cookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.newvent.auth.config.AuthProps;
import com.newvent.auth.service.RefreshTokenService;
import com.newvent.auth.web.AccountType;

/**
 * Refresh Token 쿠키 생성·삭제.
 *
 *   이름 · Path      AccountType 이 정한다 — 사용자 nv_user_rt(/api/auth), 관리자 nv_admin_rt(/api/admin/auth)
 *   Secure · SameSite 환경별 설정(auth.cookie.*)을 따른다 — 로컬 false / Lax, 배포 true / Lax
 */
@Component
public class RefreshCookies {

    private final AuthProps.Cookie props;
    private final RefreshTokenService refreshTokens;

    public RefreshCookies(AuthProps props, RefreshTokenService refreshTokens) {
        this.props = props.cookie();
        this.refreshTokens = refreshTokens;
    }

    public ResponseCookie create(AccountType accountType, String rawToken) {
        return base(accountType, rawToken).maxAge(refreshTokens.ttl()).build();
    }

    /** 같은 name/path 로 만료시켜 브라우저가 지우게 한다. */
    public ResponseCookie expire(AccountType accountType) {
        return base(accountType, "").maxAge(0).build();
    }

    /**
     * 요청에서 그 계정 종류의 Refresh Token 원문을 꺼낸다. 없으면 null.
     *
     * ★ 다른 계정 종류의 쿠키는 읽지 않는다. 관리자 refresh 에 사용자 쿠키가 실려 와도(경로가 달라 원래는 안 실린다) 없는 것으로 본다.
     */
    public String read(AccountType accountType, HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        for (Cookie c : request.getCookies()) {
            if (accountType.cookieName().equals(c.getName())) return c.getValue();
        }

        return null;
    }

    private ResponseCookie.ResponseCookieBuilder base(AccountType accountType, String value) {
        return ResponseCookie.from(accountType.cookieName(), value)
                .httpOnly(true)
                .secure(props.secure())
                .sameSite(props.sameSite())
                .path(accountType.basePath());
    }
}
