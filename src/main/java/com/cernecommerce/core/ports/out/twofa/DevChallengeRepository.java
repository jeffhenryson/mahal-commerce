package com.cernecommerce.core.ports.out.twofa;

import com.cernecommerce.core.domain.model.auth.DevChallengeToken;

import java.time.Instant;
import java.util.Optional;

public interface DevChallengeRepository {

    /** Salva o desafio DEV com o período T do primeiro código TOTP. TTL recomendado: 90s. */
    DevChallengeToken save(String username, String rawToken, long periodT, Instant expiresAt);

    Optional<DevChallengeToken> findByToken(String rawToken);

    /** Marca como consumido de forma atômica. Retorna false se já foi usado. */
    boolean consume(String rawToken);

    void deleteExpiredBefore(Instant before);
}
