package com.securityspring.core.domain.model.auth;

import java.time.Instant;

public record TotpConfig(
        Long id,
        String username,
        String secretEncrypted,
        boolean enabled,
        Instant confirmedAt) {
}
