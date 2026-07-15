package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.EmailVerificationCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailVerificationCodeJpaRepository extends JpaRepository<EmailVerificationCodeEntity, Long> {
    Optional<EmailVerificationCodeEntity> findByCode(String code);

    // Use findFirst to avoid IncorrectResultSizeDataAccessException when multiple
    // stale codes exist for the same username (e.g. after a failed email delivery
    // left the code committed but the user was later re-created without cleanup).
    Optional<EmailVerificationCodeEntity> findFirstByUsernameOrderByExpiresAtDescIdDesc(String username);

    // CAS atômico: atualiza apenas se used=false, prevenindo race condition em verificações concorrentes.
    @Modifying
    @Query("update EmailVerificationCodeEntity e set e.used = true where e.code = :code and e.used = false")
    int markAsUsedIfNotClaimed(@Param("code") String code);

    @Modifying
    @Query("delete from EmailVerificationCodeEntity e where e.username = :username")
    void deleteByUsername(@Param("username") String username);

    @Modifying
    @Query("delete from EmailVerificationCodeEntity e where e.expiresAt < :before")
    void deleteExpiredBefore(@Param("before") java.time.Instant before);
}
