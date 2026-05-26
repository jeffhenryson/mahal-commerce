package com.securityspring.adapter.out.repository;

import com.securityspring.adapter.out.entities.EmailVerificationCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailVerificationCodeJpaRepository extends JpaRepository<EmailVerificationCodeEntity, Long> {
    Optional<EmailVerificationCodeEntity> findByCode(String code);

    Optional<EmailVerificationCodeEntity> findByUsername(String username);

    // CAS atômico: atualiza apenas se used=false, prevenindo race condition em verificações concorrentes.
    @Modifying
    @Query("update EmailVerificationCodeEntity e set e.used = true where e.code = :code and e.used = false")
    int markAsUsedIfNotClaimed(@Param("code") String code);

    @Modifying
    @Query("delete from EmailVerificationCodeEntity e where e.username = :username")
    void deleteByUsername(@Param("username") String username);
}
