package com.securityspring.core.domain.model.auth;

import java.time.Instant;

public record EmailVerificationCode(
        Long id,
        String username,
        String code,
        Instant expiresAt,
        Instant sentAt,
        boolean used) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isOnCooldown(long cooldownSeconds) {
        return sentAt != null && Instant.now().isBefore(sentAt.plusSeconds(cooldownSeconds));
    }
}
