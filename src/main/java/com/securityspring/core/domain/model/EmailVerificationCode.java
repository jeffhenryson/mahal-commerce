package com.securityspring.core.domain.model;

import java.time.Instant;

public record EmailVerificationCode(
        Long id,
        String username,
        String code,
        Instant expiresAt,
        boolean used
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
