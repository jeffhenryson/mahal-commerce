package com.cernecommerce.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cernecommerce.adapter.out.persistence.entity.RefreshTokenEntity;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshTokenEntity r WHERE r.tokenHash = :hash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("hash") String hash);

    @Query("SELECT r FROM RefreshTokenEntity r WHERE r.user.username = :username AND r.revoked = false AND r.expiresAt > :now ORDER BY r.createdAt DESC")
    List<RefreshTokenEntity> findActiveByUsername(@Param("username") String username, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true, r.rotatedAt = :now " +
           "WHERE r.user.username = :username AND r.revoked = false")
    int revokeAllByUsername(@Param("username") String username, @Param("now") java.time.Instant now);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :now OR r.revoked = true")
    int deleteExpiredAndRevoked(@Param("now") Instant now);

    /** Conta sessões ativas (não expiradas, não revogadas) de um usuário. */
    @Query("SELECT COUNT(r) FROM RefreshTokenEntity r WHERE r.user.username = :username AND r.revoked = false AND r.expiresAt > :now")
    long countActiveByUsername(@Param("username") String username, @Param("now") Instant now);

    /**
     * Retorna as sessões ativas mais antigas além do limite, ordenadas da mais antiga para a mais nova.
     * Usadas para revogar sessões excedentes ao atingir o limite por usuário.
     */
    @Query("SELECT r FROM RefreshTokenEntity r WHERE r.user.username = :username AND r.revoked = false AND r.expiresAt > :now ORDER BY r.createdAt ASC")
    List<RefreshTokenEntity> findActiveByUsernameOrderByCreatedAtAsc(@Param("username") String username, @Param("now") Instant now);

    @Query("SELECT r FROM RefreshTokenEntity r WHERE r.id = :id AND r.user.username = :username AND r.revoked = false AND r.expiresAt > :now")
    Optional<RefreshTokenEntity> findActiveByIdAndUsername(
        @Param("id") Long id, @Param("username") String username, @Param("now") Instant now);

    @Query("SELECT COUNT(r) FROM RefreshTokenEntity r WHERE r.revoked = false AND r.expiresAt > :now")
    long countAllActive(@Param("now") Instant now);
}
