package com.securityspring.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.securityspring.adapter.out.persistence.entity.RefreshTokenEntity;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
    long deleteByUser_Id(Long userId);
    long deleteByExpiresAtBefore(Instant instant);

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true, r.rotatedAt = :now " +
           "WHERE r.user.username = :username AND r.revoked = false")
    int revokeAllByUsername(@Param("username") String username, @Param("now") java.time.Instant now);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :now OR r.revoked = true")
    int deleteExpiredAndRevoked(@Param("now") Instant now);
}
