package com.securityspring.core.domain.model.auth;

import java.time.Instant;

public record TotpChallengeToken(
        Long id,
        String username,
        String tokenHash,
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
