package com.newvent.auth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 삭제된 행 수를 돌려준다. */
    @Modifying(clearAutomatically = true)
    @Query(
    """
    DELETE FROM RefreshToken t
    WHERE       t.tokenHash = :tokenHash
    """)
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    /** 존재 + 미사용 + 미만료일 때만 usedAt 을 채운다 — 같은 토큰으로 동시에 갱신해도 한쪽만 1 을 받는다. */
    @Modifying(clearAutomatically = true)
    @Query(
    """
    UPDATE  RefreshToken t
    SET     t.usedAt = :now
    WHERE   t.tokenHash = :tokenHash
    AND     t.usedAt IS NULL
    AND     t.expiresAt > :now
    """)
    int markUsed(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    /** 만료 시각이 지난 토큰을 지운다 (idx_refresh_tokens_expires_at 사용). 삭제한 행 수를 돌려준다. */
    @Modifying(clearAutomatically = true)
    @Query(
    """
    DELETE FROM RefreshToken t
    WHERE       t.expiresAt <= :now
    """)
    int deleteExpired(@Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query(
    """
    DELETE FROM RefreshToken t
    WHERE       t.userId = :userId
    """)
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query(
    """
    DELETE FROM RefreshToken t
    WHERE       t.adminId = :adminId
    """)
    int deleteByAdminId(@Param("adminId") Long adminId);
}
