package com.newvent.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.newvent.auth.dto.AuthUser;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * refresh_tokens 매핑 (V6__create_refresh_tokens.sql).
 * user_id / admin_id 중 정확히 하나만 채운다 — DB 의 ck_refresh_tokens_owner 가 강제한다.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public RefreshToken(AuthUser owner, String tokenHash, Instant expiresAt) {
        if (owner.admin()) {
            this.adminId = owner.id();
        } else {
            this.userId = owner.id();
        }
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public AuthUser owner() {
        return adminId != null ? AuthUser.admin(adminId) : AuthUser.user(userId);
    }
}
