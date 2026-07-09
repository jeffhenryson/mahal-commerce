package com.cernecommerce.core.domain.model.auth;

import java.time.Instant;

public record PasswordResetToken(
        Long id,
        String username,
        String tokenHash,
        Instant expiresAt,
        Instant requestedAt,
        Instant usedAt) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
