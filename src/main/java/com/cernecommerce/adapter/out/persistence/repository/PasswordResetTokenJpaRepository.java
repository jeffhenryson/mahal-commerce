package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    // CAS atômico: marca como usado apenas se ainda não foi utilizado.
    @Modifying
    @Query("update PasswordResetTokenEntity t set t.usedAt = :now where t.tokenHash = :tokenHash and t.usedAt is null")
    int markAsUsedIfNotClaimed(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetTokenEntity t where t.username = :username")
    void deleteByUsername(@Param("username") String username);

    @Modifying
    @Query("delete from PasswordResetTokenEntity t where t.expiresAt < :before")
    void deleteExpiredBefore(@Param("before") Instant before);
}
