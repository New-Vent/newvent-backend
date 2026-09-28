package com.newvent.auth.service;

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
    public Issued loginUser(String loginId, String password) {
        User user = userRepository.findByLoginId(loginId).orElse(null);
        if (!passwordMatches(password, user != null ? user.getPasswordHash() : null) || user == null) {
            throw new InvalidCredentialsException();
        }

        user.refreshMembershipGrade(MembershipGradeService.onLogin(user.getPlan(), user.joinedAt()));

        return issue(AuthUser.user(user.getId()));
    }

    /** 비활성(is_active = false) 관리자는 비밀번호가 맞아도 거부한다 — 사유는 구분해서 알리지 않는다. */
    @Override
    @Transactional
    public Issued loginAdmin(String loginId, String password) {
        Admin admin = adminRepository.findByLoginId(loginId).orElse(null);
        if (!passwordMatches(password, admin != null ? admin.getPasswordHash() : null) || admin == null
                || !admin.isActive()) {
            throw new InvalidCredentialsException();
        }

        return issue(AuthUser.admin(admin.getId()));
    }

    /** 재사용 탐지로 인한 세션 전체 폐기가 AuthException 과 함께 롤백되지 않도록 한다 (RefreshTokenServiceImpl.rotate 참고). */
    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public Issued refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        var rotated = refreshTokens.rotate(refreshToken);
        if (!canSignIn(rotated.owner())) {
            throw new InvalidRefreshTokenException();
        }

        return new Issued(accessTokenOf(rotated.owner()), rotated.raw());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.revoke(refreshToken);
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
        return new TokenResponse(access.value(), access.expiresAt());
    }
}
