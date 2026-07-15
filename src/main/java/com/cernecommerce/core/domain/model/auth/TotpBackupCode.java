package com.cernecommerce.core.domain.model.auth;

import java.time.Instant;

public record TotpBackupCode(
        Long id,
        String username,
        String codeHash,
        Instant usedAt) {

    public boolean isUsed() {
        return usedAt != null;
    }
}
