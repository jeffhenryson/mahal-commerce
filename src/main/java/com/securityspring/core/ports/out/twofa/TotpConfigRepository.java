package com.securityspring.core.ports.out.twofa;

import com.securityspring.core.domain.model.auth.TotpConfig;

import java.time.Instant;
import java.util.Optional;

public interface TotpConfigRepository {
    TotpConfig save(String username, String secretEncrypted);

    Optional<TotpConfig> findByUsername(String username);

    void enable(String username, Instant confirmedAt);

    void deleteByUsername(String username);

    void deleteUnconfirmedBefore(java.time.Instant before);
}
