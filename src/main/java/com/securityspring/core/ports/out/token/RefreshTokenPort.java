package com.securityspring.core.ports.out.token;

import com.securityspring.core.domain.model.auth.SessionInfo;

import java.util.List;

public interface RefreshTokenPort {

    record RotationResult(String username, String newToken) {}

    String issue(String username);

    RotationResult rotate(String oldToken);

    java.util.Optional<String> revoke(String token);

    void revokeAll(String username);

    void deleteExpiredAndRevoked();

    List<SessionInfo> findActiveSessions(String username);

    /** Revoga sessão pelo ID somente se pertencer ao username. Lança SessionNotFoundException caso contrário. */
    void revokeByIdForUser(Long id, String username);
}
