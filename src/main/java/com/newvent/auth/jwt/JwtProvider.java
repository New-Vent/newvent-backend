package com.newvent.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.newvent.auth.config.AuthProps;
import com.newvent.auth.dto.AuthUser;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Access Token(JWT, HS256) 발급·검증.
 *
 *   sub   = users.id 또는 admins.id
 *   admin = true 면 admins, false 면 users 의 id
 */
@Component
public class JwtProvider {

    private static final String ADMIN_CLAIM = "admin";

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtProvider(AuthProps props) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(props.jwt().accessTtlMinutes());
    }

    public AccessToken issue(AuthUser principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTtl);
        String token = Jwts.builder()
                .subject(String.valueOf(principal.id()))
                .claim(ADMIN_CLAIM, principal.admin())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new AccessToken(token, expiresAt);
    }

    /** 서명·만료가 유효하고 필요한 claim 이 모두 있을 때만 값을 돌려준다. */
    public Optional<AuthUser> parse(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Boolean admin = claims.get(ADMIN_CLAIM, Boolean.class);
            if (claims.getSubject() == null || admin == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthUser(Long.valueOf(claims.getSubject()), admin));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record AccessToken(String value, Instant expiresAt) {}
}
