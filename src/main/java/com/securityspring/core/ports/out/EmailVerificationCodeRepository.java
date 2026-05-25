package com.securityspring.core.ports.out;

import com.securityspring.core.domain.model.EmailVerificationCode;

import java.util.Optional;

public interface EmailVerificationCodeRepository {
    EmailVerificationCode save(String username, String code, java.time.Instant expiresAt);

    Optional<EmailVerificationCode> findByCode(String code);

    void deleteByUsername(String username);
}
