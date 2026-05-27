package com.securityspring.core.ports.out.token;

import com.securityspring.core.domain.model.SessionInfo;

import java.util.List;

public interface RefreshTokenPort {

    record RotationResult(String username, String newToken) {}

    String issue(String username);

    RotationResult rotate(String oldToken);

    java.util.Optional<String> revoke(String token);

    void revokeAll(String username);

    void deleteExpiredAndRevoked();

    List<SessionInfo> findActiveSessions(String username);
}
