package com.securityspring.adapter.out.repository;

import com.securityspring.adapter.out.entities.EmailVerificationCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailVerificationCodeJpaRepository extends JpaRepository<EmailVerificationCodeEntity, Long> {
    Optional<EmailVerificationCodeEntity> findByCode(String code);

    @Modifying
    @Query("delete from EmailVerificationCodeEntity e where e.username = :username")
    void deleteByUsername(@Param("username") String username);
}
