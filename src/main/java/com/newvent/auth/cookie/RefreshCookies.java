package com.newvent.auth.cookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.newvent.auth.config.AuthProps;
import com.newvent.auth.service.RefreshTokenService;

/**
 * Refresh Token 쿠키 생성·삭제.
 * Secure / SameSite 는 프로파일별 설정(auth.cookie.*)을 따른다 — dev 는 false / Lax.
 */
@Component
public class RefreshCookies {

    private final AuthProps.Cookie props;
    private final RefreshTokenService refreshTokens;

    public RefreshCookies(AuthProps props, RefreshTokenService refreshTokens) {
        this.props = props.cookie();
        this.refreshTokens = refreshTokens;
    }

    public ResponseCookie create(String rawToken) {
        return base(rawToken).maxAge(refreshTokens.ttl()).build();
    }

    /** 같은 name/path 로 만료시켜 브라우저가 지우게 한다. */
    public ResponseCookie expire() {
        return base("").maxAge(0).build();
    }

    /** 요청에서 Refresh Token 원문을 꺼낸다. 없으면 null. */
    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        for (Cookie c : request.getCookies()) {
            if (props.name().equals(c.getName())) return c.getValue();
        }

        return null;
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(props.name(), value)
                .httpOnly(true)
                .secure(props.secure())
                .sameSite(props.sameSite())
                .path(props.path());
    }
}
