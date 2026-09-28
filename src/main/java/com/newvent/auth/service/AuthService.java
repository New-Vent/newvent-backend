package com.newvent.auth.service;

import com.newvent.auth.dto.TokenResponse;

/** 로그인(사용자 / 관리자) · Access Token 재발급 · 로그아웃. */
public interface AuthService {

    /** Access Token 응답 본문 + Refresh Token 원문(쿠키로 내려갈 값). */
    record Issued(TokenResponse body, String refreshToken) { }

    Issued loginUser(String loginId, String password);

    Issued loginAdmin(String loginId, String password);

    Issued refresh(String refreshToken);

    void logout(String refreshToken);
}
