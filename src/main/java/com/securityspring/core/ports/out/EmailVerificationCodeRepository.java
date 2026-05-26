package com.securityspring.core.ports.out;

import com.securityspring.core.domain.model.EmailVerificationCode;

import java.util.Optional;

public interface EmailVerificationCodeRepository {
    EmailVerificationCode save(String username, String code, java.time.Instant expiresAt);

    Optional<EmailVerificationCode> findByCode(String code);

    Optional<EmailVerificationCode> findByUsername(String username);

    /**
     * Marca atomicamente o código como usado (CAS: used=false → used=true).
     * Retorna true se o código foi reclamado por esta chamada; false se já estava usado
     * (outra requisição concorrente venceu a corrida).
     */
    boolean markAsUsed(String code);

    void deleteByUsername(String username);
}
