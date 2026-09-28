package com.newvent.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.auth.config.AuthProps;
import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.entity.RefreshToken;
import com.newvent.auth.exception.AuthException;
import com.newvent.auth.exception.InvalidRefreshTokenException;
import com.newvent.auth.repository.RefreshTokenRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    public RefreshTokenServiceImpl(RefreshTokenRepository repository, AuthProps props) {
        this.repository = repository;
        this.ttl = Duration.ofDays(props.jwt().refreshTtlDays());
    }

    @Override
    public Duration ttl() {
        return ttl;
    }

    @Override
    @Transactional
    public String issue(AuthUser owner) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(owner, sha256(raw), Instant.now().plus(ttl)));

        return raw;
    }

    /**
     * ★ noRollbackFor: 재사용 탐지 시 revokeAll() 로 지운 뒤 AuthException 을 던진다.
     *   기본 규칙(RuntimeException → 롤백)이면 그 삭제까지 되돌려져 다른 세션이 살아남는다.
     *   AuthServiceImpl.refresh 처럼 이 메서드를 감싸는 트랜잭션도 같은 설정이 필요하다.
     */
    @Override
    @Transactional(noRollbackFor = AuthException.class)
    public Rotated rotate(String raw) {
        String hash = sha256(raw);
        Instant now = Instant.now();

        // 존재 + 미사용 + 미만료 조건을 만족할 때만 원자적으로 usedAt 을 채운다.
        // 동시에 같은 토큰으로 두 번 요청이 와도(레이스) 한쪽만 1 을 받는다.
        if (repository.markUsed(hash, now) == 0) {
            // 없는 토큰이거나, 만료됐거나, 이미 회전에 쓰인 토큰이다.
            // usedAt 이 이미 찍혀 있는 경우 = 한 번 회전됐던 토큰이 다시 들어온 것 → 재사용/탈취 의심.
            repository.findByTokenHash(hash)
                    .filter(token -> token.getUsedAt() != null)
                    .ifPresent(token -> {
                        log.warn("Refresh Token 재사용 감지 — {} 의 모든 세션을 폐기합니다.", token.owner());
                        revokeAll(token.owner());
                    });
            throw new InvalidRefreshTokenException();
        }

        RefreshToken current = repository.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);
        return new Rotated(current.owner(), issue(current.owner()));
    }

    @Override
    @Transactional
    public void revoke(String raw) {
        repository.deleteByTokenHash(sha256(raw));
    }

    @Override
    @Transactional
    public int revokeAll(AuthUser owner) {
        return owner.admin()
                ? repository.deleteByAdminId(owner.id())
                : repository.deleteByUserId(owner.id());
    }

    @Override
    @Transactional
    public int purgeExpired() {
        return repository.deleteExpired(Instant.now());
    }

    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
