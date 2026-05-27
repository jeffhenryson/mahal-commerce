package com.securityspring.core.ports.out.twofa;

import com.securityspring.core.domain.model.auth.TotpChallengeToken;

import java.time.Instant;
import java.util.Optional;

public interface TotpChallengeTokenRepository {
    TotpChallengeToken save(String username, String rawToken, Instant expiresAt);

    Optional<TotpChallengeToken> findByToken(String rawToken);

    boolean markAsUsed(String rawToken);

    void deleteExpiredBefore(Instant before);
}
