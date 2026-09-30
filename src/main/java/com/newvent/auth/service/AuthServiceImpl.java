package com.newvent.auth.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.admin.domain.Admin;
import com.newvent.admin.repository.AdminRepository;
import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.dto.TokenResponse;
import com.newvent.auth.exception.AuthException;
import com.newvent.auth.exception.InvalidCredentialsException;
import com.newvent.auth.exception.InvalidRefreshTokenException;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.auth.web.AccountType;
import com.newvent.user.domain.User;
import com.newvent.user.repository.UserRepository;
import com.newvent.user.service.MembershipGradeService;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokens;

    /** 없는 계정도 BCrypt 비교를 한 번 해서 응답 시간으로 계정 존재 여부가 드러나지 않게 한다. */
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository, AdminRepository adminRepository,
            PasswordEncoder passwordEncoder, JwtProvider jwtProvider, RefreshTokenService refreshTokens) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokens = refreshTokens;
        this.dummyHash = passwordEncoder.encode("dummy-password");
    }

    /** 로그인 시점에 멤버십 등급을 재계산해 저장한다 (MembershipGradeService 의 3개 재계산 시점 중 하나). */
    @Override
    @Transactional
    public Issued loginUser(String loginId, String password, String previousRefreshToken) {
        User user = userRepository.findByLoginId(loginId).orElse(null);
        if (!passwordMatches(password, user != null ? user.getPasswordHash() : null) || user == null) {
            throw new InvalidCredentialsException();
        }

        user.refreshMembershipGrade(MembershipGradeService.onLogin(user.getPlan(), user.joinedAt()));

        logout(AccountType.USER, previousRefreshToken);
        return issue(AuthUser.user(user.getId()));
    }

    /** 비활성(is_active = false) 관리자는 비밀번호가 맞아도 거부한다 — 사유는 구분해서 알리지 않는다. */
    @Override
    @Transactional
    public Issued loginAdmin(String loginId, String password, String previousRefreshToken) {
        Admin admin = adminRepository.findByLoginId(loginId).orElse(null);
        if (!passwordMatches(password, admin != null ? admin.getPasswordHash() : null) || admin == null
                || !admin.isActive()) {
            throw new InvalidCredentialsException();
        }

        logout(AccountType.ADMIN, previousRefreshToken);
        return issue(AuthUser.admin(admin.getId()));
    }

    /**
     * 소비 → 주인 확인 → 발급 순서로 회전한다.
     *
     * ★ 주인이 로그인할 수 없으면(삭제·비활성 관리자) 새 토큰을 만들지 않고 그 계정의 토큰을 전부 지운다.
     *   발급을 먼저 하면 아무도 받지 못하는 토큰 행이 남고, 다른 기기의 토큰도 살아 있어
     *   관리자를 다시 활성화하면 그 세션들이 되살아난다.
     *
     * ★ noRollbackFor: 재사용 탐지·비활성 계정의 전체 폐기가 AuthException 과 함께 롤백되지 않도록 한다
     *   (RefreshTokenServiceImpl.consume 참고).
     */
    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public Issued refresh(AccountType accountType, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        AuthUser owner = refreshTokens.consume(refreshToken, accountType.admin());
        if (!canSignIn(owner)) {
            refreshTokens.revokeAll(owner);
            throw new InvalidRefreshTokenException();
        }

        return issue(owner);
    }

    @Override
    @Transactional
    public void logout(AccountType accountType, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.revoke(refreshToken, accountType.admin());
        }
    }

    private boolean passwordMatches(String raw, String hash) {
        return passwordEncoder.matches(raw, hash != null ? hash : dummyHash) && hash != null;
    }

    /** 토큰을 받은 뒤 계정이 삭제됐거나 관리자가 비활성화됐으면 더 이상 갱신해 주지 않는다. */
    private boolean canSignIn(AuthUser owner) {
        return owner.admin()
                ? adminRepository.findById(owner.id()).map(Admin::isActive).orElse(false)
                : userRepository.existsById(owner.id());
    }

    private Issued issue(AuthUser principal) {
        return new Issued(accessTokenOf(principal), refreshTokens.issue(principal));
    }

    private TokenResponse accessTokenOf(AuthUser principal) {
        var access = jwtProvider.issue(principal);
        // 발급 직후 몇 ms 가 흘러 1799.99 초가 되므로 올림한다 (버리면 30분 TTL 이 1799 로 나간다)
        long remainingMs = Duration.between(Instant.now(), access.expiresAt()).toMillis();
        long expiresIn = Math.max(0, (remainingMs + 999) / 1000);
        return new TokenResponse(access.value(), access.expiresAt(), expiresIn);
    }
}
