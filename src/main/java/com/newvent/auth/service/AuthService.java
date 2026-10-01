package com.newvent.auth.service;

import com.newvent.auth.dto.TokenResponse;
import com.newvent.auth.web.AccountType;

/** 로그인(사용자 / 관리자) · Access Token 재발급 · 로그아웃. */
public interface AuthService {

    /** Access Token 응답 본문 + Refresh Token 원문(쿠키로 내려갈 값). */
    record Issued(TokenResponse body, String refreshToken) { }

    /**
     * 로그인. previousRefreshToken 은 요청에 실려 온 같은 계정 종류의 Refresh 쿠키(없으면 null)다.
     *
     * ★ 로그인에 성공하면 그 토큰을 폐기한다. 새 쿠키가 덮어써서 브라우저는 옛 토큰을 잃지만,
     *   DB 에는 만료(7일)까지 유효하게 남기 때문이다 (다른 계정으로 다시 로그인한 경우 포함).
     *   실패하면 건드리지 않는다 — 비밀번호를 한 번 틀렸다고 지금 세션이 끊기면 안 된다.
     */
    Issued loginUser(String loginId, String password, String previousRefreshToken);

    Issued loginAdmin(String loginId, String password, String previousRefreshToken);

    /**
     * 그 계정 종류(사용자 / 관리자)의 토큰일 때만 재발급한다.
     * 다른 계정 종류의 토큰이 오면 무효로 보고, 그 토큰은 소비하지 않는다.
     */
    Issued refresh(AccountType accountType, String refreshToken);

    /** 그 계정 종류의 토큰일 때만 폐기한다. 없거나 다른 계정 종류의 토큰이면 조용히 넘어간다. */
    void logout(AccountType accountType, String refreshToken);
}
