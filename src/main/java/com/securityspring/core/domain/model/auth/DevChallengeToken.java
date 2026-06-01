package com.securityspring.core.domain.model.auth;

import java.time.Instant;

public record DevChallengeToken(
        Long id,
        String username,
        String tokenHash,
        long periodT,       // período TOTP do primeiro código (epoch / 30)
        Instant expiresAt,
        Instant createdAt,
        Instant usedAt) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
